package com.multichat.permission.dto;

import java.time.Instant;
import java.util.UUID;

public record RoomAuthorizationResponse(UUID authorizationId, UUID roomId, UUID adminUserId,
                                        UUID grantedByUserId, Instant createdAt) {
}
