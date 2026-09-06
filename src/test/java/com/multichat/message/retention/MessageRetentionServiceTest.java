package com.multichat.message.retention;

import com.multichat.audit.service.AuditService;
import com.multichat.common.exception.BusinessException;
import com.multichat.infrastructure.mapper.MessageRetentionRunMapper;
import com.multichat.permission.PermissionService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageRetentionServiceTest {
    private final MessageRetentionProperties properties = new MessageRetentionProperties();
    private final MessageRetentionRunMapper runMapper = mock(MessageRetentionRunMapper.class);
    private final MessageRetentionBatchProcessor batchProcessor = mock(MessageRetentionBatchProcessor.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final Instant now = Instant.parse("2026-09-06T12:00:00Z");

    private MessageRetentionService service() {
        return new MessageRetentionService(properties, runMapper, batchProcessor, permissionService, auditService,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void scheduledRunUsesThreeCalendarMonthCutoffAndIsIdempotentOnEmptyFollowup() {
        when(batchProcessor.purgeBatch(any(), isNull(), any(), anyInt())).thenReturn(2, 0);

        MessageRetentionRunResult result = service().runScheduled();

        assertEquals("SUCCEEDED", result.status());
        assertEquals(2, result.purgedCount());
        assertEquals(1, result.batchCount());
        assertEquals(Instant.parse("2026-06-06T12:00:00Z"), result.cutoffAt());
        verify(runMapper).complete(eq(result.runId()), eq("SUCCEEDED"), eq(now), isNull());
        verify(batchProcessor, times(2)).purgeBatch(any(), isNull(), eq(result.cutoffAt()), eq(500));
    }

    @Test
    void failedBatchIsRetriedAndItsRetryIsRecorded() {
        when(batchProcessor.purgeBatch(any(), isNull(), any(), anyInt()))
                .thenThrow(new IllegalStateException("temporary database error"))
                .thenReturn(1, 0);

        MessageRetentionRunResult result = service().runScheduled();

        assertEquals("SUCCEEDED", result.status());
        assertEquals(1, result.retryCount());
        verify(batchProcessor).recordRetry(any(), contains("temporary database error"));
    }

    @Test
    void manualRunRequiresExactConfirmationAndSystemAdmin() {
        assertThrows(BusinessException.class, () -> service().runManually("delete now"));
        verify(permissionService).requireSystemAdmin();
        verifyNoInteractions(runMapper, batchProcessor, auditService);

        reset(permissionService);
        UUID actorId = UUID.randomUUID();
        when(permissionService.currentActorId()).thenReturn(actorId);
        when(batchProcessor.purgeBatch(any(), eq(actorId), any(), anyInt())).thenReturn(0);

        MessageRetentionRunResult result = service().runManually(ManualMessageRetentionRequest.REQUIRED_CONFIRMATION);

        assertEquals("SUCCEEDED", result.status());
        verify(permissionService).requireSystemAdmin();
        verify(auditService).append(argThat(log -> actorId.equals(log.actorId())
                && "MESSAGE_RETENTION_RUN".equals(log.action())));
    }
}
