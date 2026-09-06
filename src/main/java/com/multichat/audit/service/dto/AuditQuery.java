package com.multichat.audit.service.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditQuery(UUID actorId, UUID roomId, UUID messageId, String action,
                         Instant from, Instant to, int page, int size) {
}
