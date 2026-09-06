package com.multichat.permission.dto;

import java.time.Instant;
import java.util.UUID;

public record RoomAuthorizationRevokeResponse(UUID roomId, UUID adminUserId, boolean revoked, Instant revokedAt) {
}
