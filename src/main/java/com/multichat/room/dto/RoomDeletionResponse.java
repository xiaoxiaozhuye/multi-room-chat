package com.multichat.room.dto;

import java.time.Instant;
import java.util.UUID;

public record RoomDeletionResponse(UUID roomId, String roomStatus, Instant deletedAt) {
}
