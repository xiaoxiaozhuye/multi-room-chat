package com.multichat.audit.entity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLog(UUID id, UUID requestId, UUID actorId, String action, String resourceType,
                       UUID resourceId, UUID roomId, UUID messageId, Map<String, Object> beforeState,
                       Map<String, Object> afterState, Map<String, Object> detail, Instant createdAt) {
    /** Compatibility constructor for domain writes that have no state snapshots. */
    public AuditLog(UUID id, UUID requestId, UUID actorId, String action, String resourceType,
                    UUID resourceId, UUID roomId, UUID messageId, Instant createdAt) {
        this(id, requestId, actorId, action, resourceType, resourceId, roomId, messageId,
                Map.of(), Map.of(), Map.of(), createdAt);
    }
}
