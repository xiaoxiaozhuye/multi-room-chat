package com.multichat.permission.dto;

import java.time.Instant;
import java.util.UUID;

public record RoomAuthorizationGrantResponse(UUID roomId, UUID adminUserId, Instant grantedAt) {
}
