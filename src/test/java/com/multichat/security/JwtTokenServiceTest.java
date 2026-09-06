package com.multichat.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtTokenServiceTest {
    private final JwtTokenService tokens = new JwtTokenService(new JwtProperties(
            "test-issuer", "dGVzdC1zaWduaW5nLWtleS1mb3ItbXVsdGktcm9vbS1jaGF0LTE3LWNoYXJz", Duration.ofMinutes(5)));

    @Test
    void createsTokenContainingSubjectAndRoles() {
        UUID userId = UUID.randomUUID();

        String token = tokens.createAccessToken(userId, List.of("USER"));

        assertEquals(userId.toString(), tokens.parse(token).getSubject());
        assertEquals("USER", ((List<?>) tokens.parse(token).get("roles")).get(0));
    }
}
