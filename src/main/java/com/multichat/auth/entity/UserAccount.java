package com.multichat.auth.entity;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(UUID id, String username, String email, String passwordHash, String role,
                          String status, Instant createdAt, Instant updatedAt) {
}
