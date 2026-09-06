package com.multichat.member.entity;

import java.time.Instant;
import java.util.UUID;

public record RoomMembership(UUID id, UUID userId, UUID roomId, String status, Instant requestedAt,
                             Instant activatedAt, long version) {
}
