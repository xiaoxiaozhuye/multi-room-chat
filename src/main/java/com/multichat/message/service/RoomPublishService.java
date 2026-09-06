package com.multichat.message.service;

import com.multichat.common.logging.BusinessLogger;
import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.message.entity.ChatMessage;
import com.multichat.websocket.RoomMessageNotifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The single publication gate for ordinary room messages.
 *
 * <p>The room row is locked for the entire delivery attempt.  This intentionally
 * trades a short per-room critical section for a strict externally observable
 * room_seq order.  A failed send does not mutate either the message or cursor;
 * the compensation job will therefore retry the same sequence first.</p>
 */
@Service
public class RoomPublishService {
    private final MessageMapper messageMapper;
    private final RoomMessageNotifier roomMessageNotifier;
    private final BusinessLogger businessLogger;

    public RoomPublishService(MessageMapper messageMapper, RoomMessageNotifier roomMessageNotifier) {
        this(messageMapper, roomMessageNotifier, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RoomPublishService(MessageMapper messageMapper, RoomMessageNotifier roomMessageNotifier,
                              BusinessLogger businessLogger) {
        this.messageMapper = messageMapper;
        this.roomMessageNotifier = roomMessageNotifier;
        this.businessLogger = businessLogger;
    }

    // Review/timeout calls arrive from an afterCommit callback, whose original
    // resources are still thread-bound.  A new transaction is required so the
    // publication state is actually committed independently.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishAvailable(UUID roomId) {
        publishAvailable(roomId, null);
    }

    /**
     * Publishes as much of one room's normal stream as the ordering gate
     * permits.  When targetMessageId is supplied, the result distinguishes a
     * message merely waiting behind an earlier review from an actual failed
     * delivery that requires compensation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RoomPublicationAttempt publishAvailable(UUID roomId, UUID targetMessageId) {
        return publishAvailable(roomId, targetMessageId, false);
    }

    /** Runs the same ordered gate from the periodic retry path. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensateAvailable(UUID roomId) {
        publishAvailable(roomId, null, true);
    }

    private RoomPublicationAttempt publishAvailable(UUID roomId, UUID targetMessageId, boolean compensationAttempt) {
        boolean targetPublished = false;
        if (roomId == null || messageMapper.lockRoomForPublication(roomId).isEmpty()) {
            return new RoomPublicationAttempt(false, false);
        }

        while (true) {
            ChatMessage candidate = messageMapper.findNextRoomPublishCandidateForUpdate(roomId).orElse(null);
            if (candidate == null || candidate.roomSeq() == null) {
                return new RoomPublicationAttempt(targetPublished, false);
            }

            if (isSkippable(candidate.status())) {
                log("PUBLISH_CURSOR_SKIP:" + candidate.status(), candidate);
                requireCursorAdvance(roomId, candidate.roomSeq());
                continue;
            }
            if (!"APPROVED".equals(candidate.status())) {
                // PENDING_REVIEW must hold the gate closed for all later sequences.
                log("PUBLISH_BLOCKED:" + candidate.status(), candidate);
                return new RoomPublicationAttempt(targetPublished, false);
            }

            Instant publishedAt = Instant.now();
            log(compensationAttempt ? "PUBLISH_COMPENSATION_ATTEMPT" : "PUBLISH_ATTEMPT", candidate);
            if (!roomMessageNotifier.publishApproved(candidate, publishedAt)) {
                // At-least-once: keep APPROVED and the cursor in place for a retry.
                log(compensationAttempt ? "PUBLISH_COMPENSATION_RETRY_SCHEDULED"
                        : "PUBLISH_DELIVERY_FAILED_WAITING_COMPENSATION", candidate);
                return new RoomPublicationAttempt(targetPublished,
                        targetMessageId != null && targetMessageId.equals(candidate.id()));
            }
            if (messageMapper.markApprovedPublished(candidate.id(), publishedAt) != 1) {
                throw new IllegalStateException("Approved message disappeared during publication");
            }
            requireCursorAdvance(roomId, candidate.roomSeq());
            log(compensationAttempt ? "PUBLISH_COMPENSATION_COMPLETED" : "PUBLISH_COMPLETED", candidate);
            if (targetMessageId != null && targetMessageId.equals(candidate.id())) targetPublished = true;
        }
    }

    public record RoomPublicationAttempt(boolean targetPublished, boolean targetPendingCompensation) { }

    private boolean isSkippable(String status) {
        return "REJECTED".equals(status) || "TIMEOUT".equals(status)
                || "CANCELLED_BY_ROOM_DELETION".equals(status) || "PUBLISHED".equals(status);
    }

    private void requireCursorAdvance(UUID roomId, long roomSeq) {
        if (messageMapper.advanceRoomPublishCursor(roomId, roomSeq) != 1) {
            throw new IllegalStateException("Room publication cursor changed while locked");
        }
    }

    private void log(String event, ChatMessage message) {
        if (businessLogger != null && message != null) {
            businessLogger.messageLifecycle(event, message.requestId(), message.id(), message.roomId(), message.senderId());
        }
    }
}
