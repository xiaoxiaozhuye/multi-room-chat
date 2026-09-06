package com.multichat.message.service;

import com.multichat.audit.AuditStates;
import com.multichat.audit.entity.AuditLog;
import com.multichat.audit.service.AuditService;
import com.multichat.common.logging.BusinessLogger;
import com.multichat.infrastructure.metrics.BusinessMetrics;
import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.infrastructure.redis.PendingReviewIndex;
import com.multichat.message.entity.ChatMessage;
import com.multichat.websocket.PersonalMessageNotifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/** Commits one automatic PENDING_REVIEW to TIMEOUT transition at a time. */
@Service
public class MessageTimeoutTransactionProcessor {
    private final MessageMapper messageMapper;
    private final AuditService auditService;
    private final PendingReviewIndex pendingReviewIndex;
    private final PersonalMessageNotifier personalMessageNotifier;
    private final RoomPublishService roomPublishService;
    private final BusinessLogger businessLogger;
    private final BusinessMetrics businessMetrics;

    public MessageTimeoutTransactionProcessor(MessageMapper messageMapper, AuditService auditService,
                                              PendingReviewIndex pendingReviewIndex,
                                              PersonalMessageNotifier personalMessageNotifier) {
        this(messageMapper, auditService, pendingReviewIndex, personalMessageNotifier, null, null, null);
    }

    public MessageTimeoutTransactionProcessor(MessageMapper messageMapper, AuditService auditService,
                                              PendingReviewIndex pendingReviewIndex,
                                              PersonalMessageNotifier personalMessageNotifier,
                                              RoomPublishService roomPublishService) {
        this(messageMapper, auditService, pendingReviewIndex, personalMessageNotifier, roomPublishService, null, null);
    }

    @Autowired
    public MessageTimeoutTransactionProcessor(MessageMapper messageMapper, AuditService auditService,
                                              PendingReviewIndex pendingReviewIndex,
                                              PersonalMessageNotifier personalMessageNotifier,
                                              RoomPublishService roomPublishService, BusinessLogger businessLogger,
                                              BusinessMetrics businessMetrics) {
        this.messageMapper = messageMapper;
        this.auditService = auditService;
        this.pendingReviewIndex = pendingReviewIndex;
        this.personalMessageNotifier = personalMessageNotifier;
        this.roomPublishService = roomPublishService;
        this.businessLogger = businessLogger;
        this.businessMetrics = businessMetrics;
    }

    /**
     * @return true only when this worker won the conditional terminal-state
     * transition. A false result means a concurrent review already owns it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean timeout(UUID messageId, Instant timedOutAt) {
        if (messageId == null || timedOutAt == null) {
            return false;
        }

        ChatMessage before = messageMapper.findById(messageId).orElse(null);
        if (before == null || !"PENDING_REVIEW".equals(before.status())) {
            pendingReviewIndex.remove(messageId);
            return false;
        }

        if (messageMapper.timeoutPending(messageId, timedOutAt) != 1) {
            // A human review won the race. Its ZSet member is now stale.
            pendingReviewIndex.remove(messageId);
            return false;
        }

        ChatMessage timedOut = messageMapper.findById(messageId)
                .orElseThrow(() -> new IllegalStateException("Timed-out message is missing"));
        auditService.append(new AuditLog(UUID.randomUUID(), before.requestId(), null, "MESSAGE_TIMEOUT", "MESSAGE",
                timedOut.id(), timedOut.roomId(), timedOut.id(), AuditStates.message(before), AuditStates.message(timedOut),
                AuditStates.detail("reviewResult", "TIMEOUT", "reason", "REVIEW_TIMEOUT"), timedOutAt));

        // TIMEOUT is a non-publishable terminal state, but it may unblock a
        // later approved room sequence.
        pendingReviewIndex.remove(messageId);
        notifyAfterCommit(timedOut);
        return true;
    }

    private void notifyAfterCommit(ChatMessage message) {
        Runnable work = () -> {
            if (businessMetrics != null) {
                businessMetrics.recordReviewTimeout();
                businessMetrics.recordReviewCompleted(message.createdAt(), message.reviewedAt());
            }
            log("REVIEW_TIMEOUT", message);
            log("REVIEW_RESULT_NOTIFY:TIMEOUT", message);
            personalMessageNotifier.notifyReviewResult(message, "TIMEOUT");
            if (roomPublishService != null) roomPublishService.publishAvailable(message.roomId());
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            work.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { work.run(); }
        });
    }

    private void log(String event, ChatMessage message) {
        if (businessLogger != null && message != null) {
            businessLogger.messageLifecycle(event, message.requestId(), message.id(), message.roomId(), message.senderId());
        }
    }

}
