package com.multichat.member.dto;

import java.time.Instant;
import java.util.UUID;

/** Flat database row; the controller turns it into the nested API projection. */
public record MyRoomMembershipRow(UUID membershipId, UUID userId, UUID roomId, String memberStatus,
                                  Instant createdAt, Instant joinedAt, UUID summaryRoomId,
                                  String roomName, String roomDescription) {
}
