package com.multichat.member.dto;

import java.time.Instant;
import java.util.UUID;

/** Client projection for a user's room memberships. */
public record MyRoomMembership(UUID membershipId, UUID userId, UUID roomId, String memberStatus,
                               Instant createdAt, Instant joinedAt, RoomSummary room) {
    public record RoomSummary(UUID roomId, String name, String description) {
    }
}
