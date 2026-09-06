package com.multichat.message.retention;

import com.multichat.audit.service.AuditService;
import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.infrastructure.mapper.MessageRetentionRunMapper;
import com.multichat.message.entity.ChatMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageRetentionBatchProcessorTest {
    private final MessageMapper messageMapper = mock(MessageMapper.class);
    private final MessageRetentionRunMapper runMapper = mock(MessageRetentionRunMapper.class);
    private final AuditService auditService = mock(AuditService.class);
    private final MessageRetentionBatchProcessor processor =
            new MessageRetentionBatchProcessor(messageMapper, runMapper, auditService);

    @Test
    void purgesOnlyRowsWhoseConditionalUpdateStillSucceedsAndWritesAuditEvidence() {
        UUID roomId = UUID.randomUUID();
        ChatMessage retired = new ChatMessage(UUID.randomUUID(), UUID.randomUUID(), roomId, UUID.randomUUID(), 7L,
                null, "CHAT", "expired body", "PUBLISHED", null, null, Instant.parse("2026-01-01T00:00:00Z"),
                0, Instant.parse("2026-01-01T00:00:00Z"));
        ChatMessage concurrent = new ChatMessage(UUID.randomUUID(), UUID.randomUUID(), roomId, UUID.randomUUID(), 8L,
                null, "CHAT", "already changed", "PUBLISHED", null, null, Instant.parse("2026-01-01T00:00:00Z"),
                0, Instant.parse("2026-01-01T00:00:00Z"));
        Instant cutoff = Instant.parse("2026-04-01T00:00:00Z");
        when(messageMapper.lockRetentionCandidates(cutoff, 500)).thenReturn(List.of(retired, concurrent));
        when(messageMapper.retireContent(eq(retired.id()), eq(cutoff), any())).thenReturn(1);
        when(messageMapper.retireContent(eq(concurrent.id()), eq(cutoff), any())).thenReturn(0);

        int count = processor.purgeBatch(UUID.randomUUID(), null, cutoff, 500);

        assertEquals(1, count);
        verify(auditService, times(1)).append(argThat(log -> retired.id().equals(log.messageId())
                && "MESSAGE_CONTENT_PURGED".equals(log.action())));
        verify(runMapper).addProgress(any(), eq(1), eq(1), eq(0), isNull());
    }
}
