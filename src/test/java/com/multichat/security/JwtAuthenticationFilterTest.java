package com.multichat.security;

import com.multichat.auth.entity.UserAccount;
import com.multichat.infrastructure.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {
    private final JwtTokenService tokens = new JwtTokenService(new JwtProperties("test-issuer",
            "dGVzdC1zaWduaW5nLWtleS1mb3ItbXVsdGktcm9vbS1jaGF0LTE3LWNoYXJz", Duration.ofMinutes(5)));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void serverSideRoleOverridesTheRoleSnapshotInJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UserMapper users = mock(UserMapper.class);
        when(users.findActiveById(userId)).thenReturn(Optional.of(account(userId, "ROOM_ADMIN")));
        MockHttpServletRequest request = request(tokens.createAccessToken(userId, List.of("USER")));

        new JwtAuthenticationFilter(tokens, users).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals("ROLE_ROOM_ADMIN", SecurityContextHolder.getContext().getAuthentication().getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void disabledUserWithValidJwtIsNotAuthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UserMapper users = mock(UserMapper.class);
        when(users.findActiveById(userId)).thenReturn(Optional.empty());

        new JwtAuthenticationFilter(tokens, users).doFilter(request(tokens.createAccessToken(userId, List.of("USER"))),
                new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private UserAccount account(UUID id, String role) {
        Instant now = Instant.now();
        return new UserAccount(id, "alice", "alice@example.test", "unused", role, "ACTIVE", now, now);
    }
}
