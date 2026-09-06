package com.multichat.message.retention;

import com.multichat.audit.entity.AuditLog;
import com.multichat.audit.service.AuditService;
import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import com.multichat.infrastructure.mapper.MessageRetentionRunMapper;
import com.multichat.permission.PermissionService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Service
public class MessageRetentionService {
    private final MessageRetentionProperties properties;
    private final MessageRetentionRunMapper runMapper;
    private final MessageRetentionBatchProcessor batchProcessor;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public MessageRetentionService(MessageRetentionProperties properties, MessageRetentionRunMapper runMapper,
                                   MessageRetentionBatchProcessor batchProcessor, PermissionService permissionService,
                                   AuditService auditService) {
        this(properties, runMapper, batchProcessor, permissionService, auditService, Clock.systemUTC());
    }

    MessageRetentionService(MessageRetentionProperties properties, MessageRetentionRunMapper runMapper,
                            MessageRetentionBatchProcessor batchProcessor, PermissionService permissionService,
                            AuditService auditService, Clock clock) {
        this.properties = properties;
        this.runMapper = runMapper;
        this.batchProcessor = batchProcessor;
        this.permissionService = permissionService;
        this.auditService = auditService;
        this.clock = clock;
    }

    public MessageRetentionRunResult runScheduled() {
        if (!properties.isEnabled()) return null;
        return run("SCHEDULED", null);
    }

    public MessageRetentionRunResult runManually(String confirmation) {
        permissionService.requireSystemAdmin();
        if (!ManualMessageRetentionRequest.REQUIRED_CONFIRMATION.equals(confirmation)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return run("MANUAL", permissionService.currentActorId());
    }

    private MessageRetentionRunResult run(String triggerType, UUID actorId) {
        validateProperties();
        Instant now = clock.instant();
        Instant cutoff = now.atZone(ZoneOffset.UTC).minusMonths(properties.getRetentionMonths()).toInstant();
        UUID runId = UUID.randomUUID();
        MessageRetentionRun running = new MessageRetentionRun(runId, triggerType, actorId, cutoff, "RUNNING",
                0, 0, 0, null, now, null);
        runMapper.insert(running);

        int total = 0;
        int batches = 0;
        int retries = 0;
        try {
            auditService.append(new AuditLog(UUID.randomUUID(), null, actorId, "MESSAGE_RETENTION_RUN", "MESSAGE_RETENTION_RUN",
                    runId, null, null, Map.of(), Map.of("triggerType", triggerType, "cutoffAt", cutoff.toString()),
                    Map.of("retentionMonths", properties.getRetentionMonths()), now));
            while (true) {
                RetriedBatch batch = purgeWithRetry(runId, actorId, cutoff);
                retries += batch.retries();
                if (batch.purgedCount() == 0) break;
                total += batch.purgedCount();
                batches++;
            }
            runMapper.complete(runId, "SUCCEEDED", clock.instant(), null);
            return new MessageRetentionRunResult(runId, "SUCCEEDED", cutoff, batches, total, retries, null);
        } catch (RuntimeException exception) {
            String error = safeError(exception);
            runMapper.complete(runId, "FAILED", clock.instant(), error);
            return new MessageRetentionRunResult(runId, "FAILED", cutoff, batches, total, retries, error);
        }
    }

    private RetriedBatch purgeWithRetry(UUID runId, UUID actorId, Instant cutoff) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= properties.getMaxBatchRetries(); attempt++) {
            try {
                return new RetriedBatch(batchProcessor.purgeBatch(runId, actorId, cutoff, properties.getBatchSize()), attempt);
            } catch (RuntimeException exception) {
                last = exception;
                if (attempt == properties.getMaxBatchRetries()) break;
                batchProcessor.recordRetry(runId, safeError(exception));
            }
        }
        throw last == null ? new IllegalStateException("Retention batch did not complete") : last;
    }

    private void validateProperties() {
        if (properties.getRetentionMonths() < 1 || properties.getBatchSize() < 1 || properties.getBatchSize() > 10_000
                || properties.getMaxBatchRetries() < 0 || properties.getMaxBatchRetries() > 20) {
            throw new IllegalStateException("Invalid message retention configuration");
        }
    }

    private String safeError(Exception exception) {
        String message = exception.getMessage();
        if (message == null) message = exception.getClass().getSimpleName();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private record RetriedBatch(int purgedCount, int retries) { }
}
