package com.multichat.permission.entity;

import java.time.Instant;
import java.util.UUID;

/** Historical grant row. Revoked rows are retained for auditability. */
public record RoomAdminAuthorization(UUID id, UUID adminId, UUID roomId, UUID grantedBy,
                                     Instant grantedAt, Instant revokedAt, UUID revokedBy,
                                     Instant createdAt) {
}
