package com.multichat.message.service;

import com.multichat.audit.service.AuditService;
import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.infrastructure.redis.PendingReviewIndex;
import com.multichat.message.entity.ChatMessage;
import com.multichat.websocket.PersonalMessageNotifier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MessageTimeoutTransactionProcessorTest {
    private final MessageMapper messageMapper = mock(MessageMapper.class);
    private final AuditService auditService = mock(AuditService.class);
    private final PendingReviewIndex pendingReviewIndex = mock(PendingReviewIndex.class);
    private final PersonalMessageNotifier notifier = mock(PersonalMessageNotifier.class);
    private final UUID messageId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-06T10:00:30Z");

    @Test
    void timeoutWinnerWritesAuditCleansIndexAndNotifiesSender() {
        ChatMessage pending = message("PENDING_REVIEW", 2, null);
        ChatMessage timedOut = message("TIMEOUT", 3, now);
        when(messageMapper.findById(messageId)).thenReturn(Optional.of(pending), Optional.of(timedOut));
        when(messageMapper.timeoutPending(messageId, now)).thenReturn(1);

        assertTrue(processor().timeout(messageId, now));

        verify(messageMapper).timeoutPending(messageId, now);
        verify(auditService).append(argThat(audit -> "MESSAGE_TIMEOUT".equals(audit.action())
                && audit.actorId() == null && "TIMEOUT".equals(audit.afterState().get("status"))));
        verify(pendingReviewIndex).remove(messageId);
        verify(notifier).notifyReviewResult(timedOut, "TIMEOUT");
    }

    @Test
    void concurrentHumanReviewLosesNoAuditButRemovesStaleIndex() {
        ChatMessage pending = message("PENDING_REVIEW", 2, null);
        when(messageMapper.findById(messageId)).thenReturn(Optional.of(pending));
        when(messageMapper.timeoutPending(messageId, now)).thenReturn(0);

        assertFalse(processor().timeout(messageId, now));

        verify(messageMapper).timeoutPending(messageId, now);
        verify(pendingReviewIndex).remove(messageId);
        verifyNoInteractions(auditService, notifier);
        verify(messageMapper, never()).drainRoomPublishQueue(any());
    }

    @Test
    void terminalCandidateOnlyCleansStaleIndex() {
        when(messageMapper.findById(messageId)).thenReturn(Optional.of(message("REJECTED", 3, now)));

        assertFalse(processor().timeout(messageId, now));

        verify(messageMapper, never()).timeoutPending(any(), any());
        verify(pendingReviewIndex).remove(messageId);
        verifyNoInteractions(auditService, notifier);
    }

    private MessageTimeoutTransactionProcessor processor() {
        return new MessageTimeoutTransactionProcessor(messageMapper, auditService, pendingReviewIndex, notifier);
    }

    private ChatMessage message(String status, long version, Instant reviewedAt) {
        return new ChatMessage(messageId, UUID.randomUUID(), roomId, UUID.randomUUID(), 4L, null, "CHAT", "hello", status,
                now, reviewedAt, null, version, now.minusSeconds(30));
    }
}
