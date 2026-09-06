package com.multichat.message.service;

import com.multichat.audit.service.AuditService;
import com.multichat.common.exception.MessageAlreadyReviewedException;
import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.infrastructure.redis.PendingReviewIndex;
import com.multichat.message.entity.ChatMessage;
import com.multichat.permission.PermissionService;
import com.multichat.websocket.PersonalMessageNotifier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MessageReviewTransactionProcessorTest {
    private final MessageMapper messageMapper = mock(MessageMapper.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final PendingReviewIndex pendingReviewIndex = mock(PendingReviewIndex.class);
    private final PersonalMessageNotifier notifier = mock(PersonalMessageNotifier.class);
    private final UUID messageId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();
    private final UUID senderId = UUID.randomUUID();
    private final UUID reviewerId = UUID.randomUUID();

    @Test
    void approvesWithOneConditionalTransitionAndCompletesAuditIndexAndPersonalNotification() {
        ChatMessage pending = message("PENDING_REVIEW", 2, null);
        ChatMessage approved = message("APPROVED", 3, Instant.parse("2026-09-06T10:00:01Z"));
        when(messageMapper.findById(messageId)).thenReturn(Optional.of(pending), Optional.of(approved), Optional.of(approved));
        when(permissionService.currentActorId()).thenReturn(reviewerId);
        when(permissionService.isSystemAdmin()).thenReturn(false);
        when(messageMapper.reviewPending(eq(messageId), eq("APPROVED"), eq(reviewerId), eq(false), any())).thenReturn(1);

        ChatMessage result = processor().review(messageId, ReviewAction.APPROVE);

        assertEquals("APPROVED", result.status());
        verify(permissionService).requireRoomPermission(eq(roomId), any());
        verify(messageMapper).reviewPending(eq(messageId), eq("APPROVED"), eq(reviewerId), eq(false), any());
        verify(auditService).append(any());
        verify(pendingReviewIndex).remove(messageId);
        verify(notifier).notifyReviewResult(approved, "APPROVED");
    }

    @Test
    void losesReviewRaceWithoutWritingAuditOrClearingTheIndex() {
        ChatMessage pending = message("PENDING_REVIEW", 2, null);
        ChatMessage timeout = message("TIMEOUT", 3, Instant.parse("2026-09-06T10:00:01Z"));
        when(messageMapper.findById(messageId)).thenReturn(Optional.of(pending), Optional.of(timeout));
        when(permissionService.currentActorId()).thenReturn(reviewerId);
        when(permissionService.isSystemAdmin()).thenReturn(true);
        when(messageMapper.reviewPending(eq(messageId), eq("REJECTED"), eq(reviewerId), eq(true), any())).thenReturn(0);

        assertThrows(MessageAlreadyReviewedException.class, () -> processor().review(messageId, ReviewAction.REJECT));

        verifyNoInteractions(auditService, pendingReviewIndex, notifier);
    }

    private MessageReviewTransactionProcessor processor() {
        return new MessageReviewTransactionProcessor(messageMapper, permissionService, auditService, pendingReviewIndex, notifier);
    }

    private ChatMessage message(String status, long version, Instant reviewedAt) {
        return new ChatMessage(messageId, UUID.randomUUID(), roomId, senderId, 4L, null, "CHAT", "hello", status,
                Instant.parse("2026-09-06T10:00:30Z"), reviewedAt, null, version, Instant.parse("2026-09-06T10:00:00Z"));
    }
}
