package com.multichat.message.service;

import com.multichat.audit.AuditStates;
import com.multichat.audit.entity.AuditLog;
import com.multichat.audit.service.AuditService;
import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import com.multichat.common.exception.MessageAlreadyReviewedException;
import com.multichat.common.logging.BusinessLogger;
import com.multichat.infrastructure.metrics.BusinessMetrics;
import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.infrastructure.redis.PendingReviewIndex;
import com.multichat.message.entity.ChatMessage;
import com.multichat.permission.AdminOperation;
import com.multichat.permission.PermissionService;
import com.multichat.websocket.PersonalMessageNotifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/** One independently committed review command, including its audit trail. */
@Service
public class MessageReviewTransactionProcessor {
    private final MessageMapper messageMapper;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final PendingReviewIndex pendingReviewIndex;
    private final PersonalMessageNotifier personalMessageNotifier;
    private final RoomPublishService roomPublishService;
    private final BusinessLogger businessLogger;
    private final BusinessMetrics businessMetrics;

    public MessageReviewTransactionProcessor(MessageMapper messageMapper, PermissionService permissionService,
                                             AuditService auditService, PendingReviewIndex pendingReviewIndex,
                                             PersonalMessageNotifier personalMessageNotifier) {
        this(messageMapper, permissionService, auditService, pendingReviewIndex, personalMessageNotifier, null, null, null);
    }

    public MessageReviewTransactionProcessor(MessageMapper messageMapper, PermissionService permissionService,
                                             AuditService auditService, PendingReviewIndex pendingReviewIndex,
                                             PersonalMessageNotifier personalMessageNotifier,
                                             RoomPublishService roomPublishService) {
        this(messageMapper, permissionService, auditService, pendingReviewIndex, personalMessageNotifier,
                roomPublishService, null, null);
    }

    @Autowired
    public MessageReviewTransactionProcessor(MessageMapper messageMapper, PermissionService permissionService,
                                             AuditService auditService, PendingReviewIndex pendingReviewIndex,
                                             PersonalMessageNotifier personalMessageNotifier,
                                             RoomPublishService roomPublishService, BusinessLogger businessLogger,
                                             BusinessMetrics businessMetrics) {
        this.messageMapper = messageMapper;
        this.permissionService = permissionService;
        this.auditService = auditService;
        this.pendingReviewIndex = pendingReviewIndex;
        this.personalMessageNotifier = personalMessageNotifier;
        this.roomPublishService = roomPublishService;
        this.businessLogger = businessLogger;
        this.businessMetrics = businessMetrics;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatMessage review(UUID messageId, ReviewAction action) {
        ChatMessage before = messageMapper.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        log("REVIEW_RECEIVED:" + action.targetStatus(), before, null);
        permissionService.requireRoomPermission(before.roomId(), AdminOperation.MESSAGE_REVIEW);
        if (!"CHAT".equals(before.messageType()) || !"PENDING_REVIEW".equals(before.status())) {
            throw new MessageAlreadyReviewedException();
        }

        UUID actorId = permissionService.currentActorId();
        boolean systemAdmin = permissionService.isSystemAdmin();
        Instant now = Instant.now();
        if (messageMapper.reviewPending(messageId, action.targetStatus(), actorId, systemAdmin, now) != 1) {
            // A concurrent revoke is reported as authorization failure; any
            // terminal state means an administrator or timeout worker won.
            ChatMessage current = messageMapper.findById(messageId).orElse(null);
            if (current != null && "PENDING_REVIEW".equals(current.status())) {
                permissionService.requireRoomPermission(current.roomId(), AdminOperation.MESSAGE_REVIEW);
            }
            throw new MessageAlreadyReviewedException();
        }

        ChatMessage reviewed = messageMapper.findById(messageId)
                .orElseThrow(() -> new IllegalStateException("Reviewed message is missing"));
        auditService.append(new AuditLog(UUID.randomUUID(), null, actorId, action.auditAction(), "MESSAGE",
                reviewed.id(), reviewed.roomId(), reviewed.id(), AuditStates.message(before), AuditStates.message(reviewed),
                AuditStates.detail("reviewResult", action.targetStatus()), now));

        // This is intentionally part of the command transaction after the
        // authoritative state and immutable audit event have been written.
        pendingReviewIndex.remove(reviewed.id());
        ChatMessage result = messageMapper.findById(messageId)
                .orElseThrow(() -> new IllegalStateException("Reviewed message is missing after publication drain"));
        notifyAfterCommit(result, action.targetStatus(), actorId);
        return result;
    }

    private void notifyAfterCommit(ChatMessage message, String reviewResult, UUID actorId) {
        Runnable work = () -> {
            if (businessMetrics != null) businessMetrics.recordReviewCompleted(message.createdAt(), message.reviewedAt());
            log("REVIEW_COMPLETED:" + reviewResult, message, actorId);
            log("REVIEW_RESULT_NOTIFY:" + reviewResult, message, null);
            personalMessageNotifier.notifyReviewResult(message, reviewResult);
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

    private void log(String event, ChatMessage message, UUID actorId) {
        if (businessLogger != null && message != null) {
            businessLogger.messageLifecycle(event, message.requestId(), message.id(), message.roomId(),
                    actorId == null ? message.senderId() : actorId);
        }
    }

}
