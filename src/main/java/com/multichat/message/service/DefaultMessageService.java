package com.multichat.message.service;

import com.multichat.audit.AuditStates;
import com.multichat.audit.ReviewProperties;
import com.multichat.audit.entity.AuditLog;
import com.multichat.audit.service.AuditService;
import com.multichat.auth.entity.UserAccount;
import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.infrastructure.mapper.RoomMembershipMapper;
import com.multichat.infrastructure.mapper.UserMapper;
import com.multichat.infrastructure.redis.PendingReviewIndex;
import com.multichat.infrastructure.redis.RateLimitDecision;
import com.multichat.infrastructure.redis.UserMessageRateLimiter;
import com.multichat.message.dto.SubmitMessageRequest;
import com.multichat.message.entity.ChatMessage;
import com.multichat.room.RoomStatePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Authoritative ordinary-message submission workflow. */
@Service
public class DefaultMessageService implements MessageService {
    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final RoomMembershipMapper membershipMapper;
    private final RoomStatePolicy roomStatePolicy;
    private final MessageContentValidator contentValidator;
    private final SensitiveContentMatcher sensitiveContentMatcher;
    private final UserMessageRateLimiter rateLimiter;
    private final PendingReviewIndex pendingReviewIndex;
    private final ModerationProperties moderationProperties;
    private final ModerationSettingsService moderationSettingsService;
    private final ReviewProperties reviewProperties;
    private final AuditService auditService;
    private final RoomPublishService roomPublishService;
    private final Clock clock;

    @Autowired
    public DefaultMessageService(MessageMapper messageMapper, UserMapper userMapper,
                                 RoomMembershipMapper membershipMapper, RoomStatePolicy roomStatePolicy,
                                 MessageContentValidator contentValidator, SensitiveContentMatcher sensitiveContentMatcher,
                                 UserMessageRateLimiter rateLimiter, PendingReviewIndex pendingReviewIndex,
                                 ModerationProperties moderationProperties, ReviewProperties reviewProperties,
                                 AuditService auditService, RoomPublishService roomPublishService,
                                 ModerationSettingsService moderationSettingsService) {
        this(messageMapper, userMapper, membershipMapper, roomStatePolicy, contentValidator, sensitiveContentMatcher,
                rateLimiter, pendingReviewIndex, moderationProperties, reviewProperties, auditService,
                roomPublishService, moderationSettingsService, Clock.systemUTC());
    }

    DefaultMessageService(MessageMapper messageMapper, UserMapper userMapper,
                          RoomMembershipMapper membershipMapper, RoomStatePolicy roomStatePolicy,
                          MessageContentValidator contentValidator, SensitiveContentMatcher sensitiveContentMatcher,
                          UserMessageRateLimiter rateLimiter, PendingReviewIndex pendingReviewIndex,
                          ModerationProperties moderationProperties, ReviewProperties reviewProperties,
                          AuditService auditService, Clock clock) {
        this(messageMapper, userMapper, membershipMapper, roomStatePolicy, contentValidator, sensitiveContentMatcher,
                rateLimiter, pendingReviewIndex, moderationProperties, reviewProperties, auditService, null, clock);
    }

    DefaultMessageService(MessageMapper messageMapper, UserMapper userMapper,
                          RoomMembershipMapper membershipMapper, RoomStatePolicy roomStatePolicy,
                          MessageContentValidator contentValidator, SensitiveContentMatcher sensitiveContentMatcher,
                          UserMessageRateLimiter rateLimiter, PendingReviewIndex pendingReviewIndex,
                          ModerationProperties moderationProperties, ReviewProperties reviewProperties,
                          AuditService auditService, RoomPublishService roomPublishService, Clock clock) {
        this(messageMapper, userMapper, membershipMapper, roomStatePolicy, contentValidator, sensitiveContentMatcher,
                rateLimiter, pendingReviewIndex, moderationProperties, reviewProperties, auditService,
                roomPublishService, null, clock);
    }

    DefaultMessageService(MessageMapper messageMapper, UserMapper userMapper,
                          RoomMembershipMapper membershipMapper, RoomStatePolicy roomStatePolicy,
                          MessageContentValidator contentValidator, SensitiveContentMatcher sensitiveContentMatcher,
                          UserMessageRateLimiter rateLimiter, PendingReviewIndex pendingReviewIndex,
                          ModerationProperties moderationProperties, ReviewProperties reviewProperties,
                          AuditService auditService, RoomPublishService roomPublishService,
                          ModerationSettingsService moderationSettingsService, Clock clock) {
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
        this.membershipMapper = membershipMapper;
        this.roomStatePolicy = roomStatePolicy;
        this.contentValidator = contentValidator;
        this.sensitiveContentMatcher = sensitiveContentMatcher;
        this.rateLimiter = rateLimiter;
        this.pendingReviewIndex = pendingReviewIndex;
        this.moderationProperties = moderationProperties;
        this.moderationSettingsService = moderationSettingsService;
        this.reviewProperties = reviewProperties;
        this.auditService = auditService;
        this.roomPublishService = roomPublishService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ChatMessage submit(UUID senderId, UUID requestId, SubmitMessageRequest request) {
        if (senderId == null || requestId == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        ChatMessage existing = messageMapper.findBySenderAndRequestId(senderId, requestId).orElse(null);
        if (existing != null) return existing;

        // Locking the active sender row makes concurrent retries re-check the
        // idempotency record only after the first transaction has committed.
        UserAccount user = userMapper.findActiveByIdForUpdate(senderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        existing = messageMapper.findBySenderAndRequestId(senderId, requestId).orElse(null);
        if (existing != null) return existing;

        validateSubmission(senderId, request, user);
        RateLimitDecision decision = rateLimiter.tryAcquire(senderId);
        if (!decision.allowed()) throw new BusinessException(ErrorCode.MESSAGE_RATE_LIMITED);

        Instant createdAt = clock.instant();
        boolean moderationEnabled = moderationEnabled(request.roomId());
        ChatMessage candidate = new ChatMessage(UUID.randomUUID(), requestId, request.roomId(), senderId, null, null,
                "CHAT", request.content(), moderationEnabled ? "PENDING_REVIEW" : "APPROVED",
                moderationEnabled ? createdAt.plus(reviewProperties.timeout()) : null,
                null, null, 0, createdAt);
        int inserted = moderationEnabled
                ? messageMapper.insertPendingChat(candidate)
                : messageMapper.insertApprovedChat(candidate);
        if (inserted != 1) {
            throw new IllegalStateException("Message insert did not create a row");
        }
        ChatMessage message = messageMapper.findById(candidate.id())
                .orElseThrow(() -> new IllegalStateException("Inserted message is missing"));

        auditService.append(new AuditLog(UUID.randomUUID(), requestId, senderId, "MESSAGE_SUBMIT", "MESSAGE",
                message.id(), message.roomId(), message.id(), null, AuditStates.message(message),
                AuditStates.detail("messageType", message.messageType()), message.createdAt()));
        if (moderationEnabled) addToReviewIndexAfterCommit(message);
        else publishAfterCommit(message.roomId());
        return message;
    }

    private void validateSubmission(UUID senderId, SubmitMessageRequest request, UserAccount user) {
        if (senderId == null || request == null || request.roomId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        // Retain this variable so a disabled/deleted account cannot be mistaken for a membership failure.
        if (!"ACTIVE".equals(user.status())) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        roomStatePolicy.requireUserChatAllowed(request.roomId());
        if (membershipMapper.findActive(senderId, request.roomId()).isEmpty()) {
            throw new BusinessException(ErrorCode.ROOM_ACCESS_DENIED);
        }
        contentValidator.requireSendable(request.content());
        if (moderationEnabled(request.roomId()) && sensitiveContentMatcher.matches(request.content())) {
            throw new BusinessException(ErrorCode.SENSITIVE_CONTENT_REJECTED);
        }
    }

    private void addToReviewIndexAfterCommit(ChatMessage message) {
        Runnable write = () -> pendingReviewIndex.add(message.id(), message.reviewDeadlineAt());
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            write.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { write.run(); }
        });
    }

    private boolean moderationEnabled(UUID roomId) {
        return moderationSettingsService == null ? moderationProperties.enabled() : moderationSettingsService.isEnabled(roomId);
    }

    private void publishAfterCommit(UUID roomId) {
        if (roomPublishService == null) return;
        Runnable publish = () -> roomPublishService.publishAvailable(roomId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { publish.run(); }
        });
    }
}
