package com.multichat.room.entity;

import java.time.Instant;
import java.util.UUID;

public record ChatRoom(UUID id, String name, String description, int maxMembers, String joinMode,
                       String status, UUID createdBy, long version, Instant createdAt, Instant updatedAt) {
}
