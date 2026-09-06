package com.multichat.message.service;

import com.multichat.infrastructure.redis.PendingReviewDueScanner;
import com.multichat.infrastructure.redis.RedisInfrastructureProperties;
import com.multichat.message.entity.PendingReviewMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PendingReviewTimeoutSchedulerTest {
    private final PendingReviewDueScanner scanner = mock(PendingReviewDueScanner.class);
    private final MessageTimeoutTransactionProcessor processor = mock(MessageTimeoutTransactionProcessor.class);

    @Test
    void scansBoundedDueCandidatesAndProcessesEachInItsOwnTransaction() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        RedisInfrastructureProperties properties = new RedisInfrastructureProperties();
        properties.setReviewScanBatchSize(2);
        when(scanner.findDue(any(), eq(2))).thenReturn(List.of(
                new PendingReviewMessage(first, Instant.now()), new PendingReviewMessage(second, Instant.now())));

        new PendingReviewTimeoutScheduler(scanner, processor, properties).scanDueMessages();

        verify(processor).timeout(eq(first), any());
        verify(processor).timeout(eq(second), any());
    }
}
