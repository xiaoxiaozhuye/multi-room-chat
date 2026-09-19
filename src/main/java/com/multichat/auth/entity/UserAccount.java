package com.multichat.auth.entity;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(UUID id, String username, String email, String passwordHash, String role,
                          String status, Instant createdAt, Instant updatedAt, String displayName,
                          String avatarUrl, int level, String bio) {
    public UserAccount(UUID id, String username, String email, String passwordHash, String role,
                       String status, Instant createdAt, Instant updatedAt) {
        this(id, username, email, passwordHash, role, status, createdAt, updatedAt,
                username, null, 1, null);
    }
}
