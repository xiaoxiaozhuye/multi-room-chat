package com.multichat.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public account projection. Password credentials must never be serialized. */
public record AuthenticatedUserResponse(UUID userId, String username, String email, List<String> roles,
                                        Instant createdAt) {
}
