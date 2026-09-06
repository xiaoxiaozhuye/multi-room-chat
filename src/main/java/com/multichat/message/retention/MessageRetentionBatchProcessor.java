package com.multichat.message.retention;

import com.multichat.audit.entity.AuditLog;
import com.multichat.audit.service.AuditService;
import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.infrastructure.mapper.MessageRetentionRunMapper;
import com.multichat.message.entity.ChatMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Processes one transactional page so a failure can be retried without a partial page. */
@Service
public class MessageRetentionBatchProcessor {
    private final MessageMapper messageMapper;
    private final MessageRetentionRunMapper runMapper;
    private final AuditService auditService;

    public MessageRetentionBatchProcessor(MessageMapper messageMapper, MessageRetentionRunMapper runMapper,
                                          AuditService auditService) {
        this.messageMapper = messageMapper;
        this.runMapper = runMapper;
        this.auditService = auditService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeBatch(UUID runId, UUID actorId, Instant cutoffAt, int batchSize) {
        List<ChatMessage> candidates = messageMapper.lockRetentionCandidates(cutoffAt, batchSize);
        if (candidates.isEmpty()) return 0;
        Instant retiredAt = Instant.now();
        int purged = 0;
        for (ChatMessage message : candidates) {
            if (messageMapper.retireContent(message.id(), cutoffAt, retiredAt) != 1) continue;
            purged++;
            // Body text is deliberately absent from audit snapshots.  The message
            // row stays in place, so this audit event retains valid room/message links.
            auditService.append(new AuditLog(UUID.randomUUID(), null, actorId, "MESSAGE_CONTENT_PURGED", "MESSAGE",
                    message.id(), message.roomId(), message.id(),
                    Map.of("status", message.status(), "contentRetired", false),
                    Map.of("status", message.status(), "contentRetired", true, "contentRetiredAt", retiredAt.toString()),
                    Map.of("retentionRunId", runId.toString(), "cutoffAt", cutoffAt.toString()), retiredAt));
        }
        runMapper.addProgress(runId, 1, purged, 0, null);
        return purged;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRetry(UUID runId, String error) {
        runMapper.addProgress(runId, 0, 0, 1, truncate(error));
    }

    private String truncate(String error) {
        if (error == null) return null;
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }
}
