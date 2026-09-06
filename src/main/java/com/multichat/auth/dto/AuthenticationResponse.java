package com.multichat.auth.dto;

import java.time.Instant;

public record AuthenticationResponse(AuthenticatedUserResponse user, String accessToken, Instant expiresAt) {
}
