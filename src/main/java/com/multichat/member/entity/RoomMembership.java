package com.multichat.member.entity;

import java.time.Instant;
import java.util.UUID;

public record RoomMembership(UUID id, UUID userId, UUID roomId, String status, Instant requestedAt,
                             Instant activatedAt, long version, String displayName, String avatarUrl,
                             int level, String bio) {
    public RoomMembership(UUID id, UUID userId, UUID roomId, String status, Instant requestedAt,
                          Instant activatedAt, long version) {
        this(id, userId, roomId, status, requestedAt, activatedAt, version, null, null, 1, null);
    }
}
