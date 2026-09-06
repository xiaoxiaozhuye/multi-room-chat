package com.multichat.audit.entity;

import java.time.Instant;
import java.util.UUID;

public record AuditLog(UUID id, UUID requestId, UUID actorId, String action, String resourceType,
                       UUID resourceId, UUID roomId, UUID messageId, Instant createdAt) {
}
