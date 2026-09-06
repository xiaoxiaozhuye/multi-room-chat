package com.multichat.message.service;

import com.multichat.audit.ReviewProperties;
import com.multichat.audit.service.AuditService;
import com.multichat.auth.entity.UserAccount;
import com.multichat.common.exception.BusinessException;
import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.infrastructure.mapper.RoomMembershipMapper;
import com.multichat.infrastructure.mapper.UserMapper;
import com.multichat.infrastructure.redis.PendingReviewIndex;
import com.multichat.infrastructure.redis.RateLimitDecision;
import com.multichat.infrastructure.redis.UserMessageRateLimiter;
import com.multichat.member.entity.RoomMembership;
import com.multichat.message.dto.SubmitMessageRequest;
import com.multichat.message.entity.ChatMessage;
import com.multichat.room.RoomStatePolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DefaultMessageServiceTest {
    private final MessageMapper messageMapper = mock(MessageMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final RoomMembershipMapper membershipMapper = mock(RoomMembershipMapper.class);
    private final RoomStatePolicy roomStatePolicy = mock(RoomStatePolicy.class);
    private final UserMessageRateLimiter rateLimiter = mock(UserMessageRateLimiter.class);
    private final PendingReviewIndex pendingReviewIndex = mock(PendingReviewIndex.class);
    private final AuditService auditService = mock(AuditService.class);
    private final Instant now = Instant.parse("2026-09-06T10:00:00Z");
    private final UUID senderId = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();

    @Test
    void storesOnePendingMessageAuditsItAndAddsItsPersistedDeadlineToRedis() {
        DefaultMessageService service = service(List.of());
        when(messageMapper.findBySenderAndRequestId(senderId, requestId)).thenReturn(Optional.empty());
        when(userMapper.findActiveByIdForUpdate(senderId)).thenReturn(Optional.of(activeUser()));
        when(membershipMapper.findActive(senderId, roomId)).thenReturn(Optional.of(mock(RoomMembership.class)));
        when(rateLimiter.tryAcquire(senderId)).thenReturn(RateLimitDecision.permitted());
        when(messageMapper.findById(any())).thenAnswer(invocation -> {
            ChatMessage inserted = invocation.getArgument(0) instanceof UUID id
                    ? capturedMessage.id().equals(id) ? capturedMessage : null : null;
            return Optional.ofNullable(inserted);
        });

        ChatMessage result = service.submit(senderId, requestId, new SubmitMessageRequest(roomId, "hello"));

        assertEquals("PENDING_REVIEW", result.status());
        assertEquals(7L, result.roomSeq());
        verify(auditService).append(any());
        verify(pendingReviewIndex).add(result.id(), result.reviewDeadlineAt());
    }

    @Test
    void reviewTimeoutSecondsDefinesThePersistedDeadline() {
        DefaultMessageService service = service(List.of(), 45);
        when(messageMapper.findBySenderAndRequestId(senderId, requestId)).thenReturn(Optional.empty());
        when(userMapper.findActiveByIdForUpdate(senderId)).thenReturn(Optional.of(activeUser()));
        when(membershipMapper.findActive(senderId, roomId)).thenReturn(Optional.of(mock(RoomMembership.class)));
        when(rateLimiter.tryAcquire(senderId)).thenReturn(RateLimitDecision.permitted());
        when(messageMapper.findById(any())).thenAnswer(invocation -> Optional.of(capturedMessage));

        ChatMessage result = service.submit(senderId, requestId, new SubmitMessageRequest(roomId, "hello"));

        assertEquals(now.plusSeconds(45), result.reviewDeadlineAt());
    }

    private ChatMessage capturedMessage;

    @Test
    void duplicateRequestReturnsOriginalWithoutRevalidatingOrWritingAnything() {
        ChatMessage original = message(UUID.randomUUID(), "first", 7L);
        when(messageMapper.findBySenderAndRequestId(senderId, requestId)).thenReturn(Optional.of(original));

        ChatMessage result = service(List.of("blocked"))
                .submit(senderId, requestId, new SubmitMessageRequest(roomId, "blocked"));

        assertSame(original, result);
        verify(messageMapper, never()).insertPendingChat(any());
        verifyNoInteractions(userMapper, membershipMapper, roomStatePolicy, rateLimiter, pendingReviewIndex, auditService);
    }

    @Test
    void sensitiveOrRateLimitedContentNeverCreatesMessageOrReviewQueueEntry() {
        DefaultMessageService sensitive = service(List.of("blocked"));
        when(messageMapper.findBySenderAndRequestId(senderId, requestId)).thenReturn(Optional.empty());
        when(userMapper.findActiveByIdForUpdate(senderId)).thenReturn(Optional.of(activeUser()));
        when(membershipMapper.findActive(senderId, roomId)).thenReturn(Optional.of(mock(RoomMembership.class)));

        BusinessException sensitiveError = assertThrows(BusinessException.class,
                () -> sensitive.submit(senderId, requestId, new SubmitMessageRequest(roomId, "contains BLOCKED text")));
        assertEquals("SENSITIVE_CONTENT_REJECTED", sensitiveError.code());
        verify(messageMapper, never()).insertPendingChat(any());
        verifyNoInteractions(rateLimiter, pendingReviewIndex);

        reset(messageMapper, rateLimiter, pendingReviewIndex);
        when(messageMapper.findBySenderAndRequestId(senderId, requestId)).thenReturn(Optional.empty());
        when(rateLimiter.tryAcquire(senderId)).thenReturn(RateLimitDecision.rejected(java.time.Duration.ofSeconds(1)));

        BusinessException rateError = assertThrows(BusinessException.class,
                () -> service(List.of()).submit(senderId, requestId, new SubmitMessageRequest(roomId, "ordinary")));
        assertEquals("MESSAGE_RATE_LIMITED", rateError.code());
        verify(messageMapper, never()).insertPendingChat(any());
        verifyNoInteractions(pendingReviewIndex);
    }

    @Test
    void contentBoundaryCountsUnicodeCodePointsAndRejectsUnpairedSurrogates() {
        MessageContentValidator validator = new MessageContentValidator();
        validator.requireSendable("😀".repeat(320));
        assertThrows(BusinessException.class, () -> validator.requireSendable("😀".repeat(321)));
        assertThrows(BusinessException.class, () -> validator.requireSendable("bad\uD800"));
    }

    private DefaultMessageService service(List<String> sensitiveWords) {
        return service(sensitiveWords, 30);
    }

    private DefaultMessageService service(List<String> sensitiveWords, int timeoutSeconds) {
        SensitiveContentMatcher matcher = new SensitiveContentMatcher(
                new ModerationProperties(sensitiveWords, "CONTAINS", java.time.Duration.ofSeconds(30)));
        DefaultMessageService service = new DefaultMessageService(messageMapper, userMapper, membershipMapper,
                roomStatePolicy, new MessageContentValidator(), matcher, rateLimiter, pendingReviewIndex,
                new ModerationProperties(sensitiveWords, "CONTAINS", java.time.Duration.ofSeconds(30)),
                new ReviewProperties(timeoutSeconds, java.time.Duration.ofSeconds(1)), auditService,
                Clock.fixed(now, ZoneOffset.UTC));
        doAnswer(invocation -> {
            ChatMessage candidate = invocation.getArgument(0);
            capturedMessage = new ChatMessage(candidate.id(), candidate.requestId(), candidate.roomId(), candidate.senderId(),
                    7L, null, candidate.messageType(), candidate.content(), candidate.status(), candidate.reviewDeadlineAt(),
                    null, null, 0, candidate.createdAt());
            return 1;
        }).when(messageMapper).insertPendingChat(any());
        return service;
    }

    private UserAccount activeUser() {
        return new UserAccount(senderId, "alice", "alice@example.test", "hash", "USER", "ACTIVE", now, now);
    }

    private ChatMessage message(UUID id, String content, long roomSeq) {
        return new ChatMessage(id, requestId, roomId, senderId, roomSeq, null, "CHAT", content, "PENDING_REVIEW",
                now.plusSeconds(30), null, null, 0, now);
    }
}
