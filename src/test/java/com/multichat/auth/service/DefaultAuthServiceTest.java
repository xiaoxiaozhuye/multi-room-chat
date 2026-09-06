package com.multichat.auth.service;

import com.multichat.audit.service.AuditService;
import com.multichat.auth.dto.LoginRequest;
import com.multichat.auth.dto.RegisterRequest;
import com.multichat.auth.entity.UserAccount;
import com.multichat.common.exception.BusinessException;
import com.multichat.infrastructure.mapper.UserMapper;
import com.multichat.security.JwtProperties;
import com.multichat.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAuthServiceTest {
    private static final JwtProperties JWT_PROPERTIES = new JwtProperties("test-issuer",
            "dGVzdC1zaWduaW5nLWtleS1mb3ItbXVsdGktcm9vbS1jaGF0LTE3LWNoYXJz", Duration.ofMinutes(5));

    @Mock private UserMapper userMapper;
    @Mock private AuditService auditService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void registerHashesPasswordAndDoesNotExposeIt() {
        DefaultAuthService service = service();

        var result = service.register(new RegisterRequest("alice_01", "Alice@Example.test", "correct-pass"));

        ArgumentCaptor<UserAccount> inserted = ArgumentCaptor.forClass(UserAccount.class);
        verify(userMapper).insert(inserted.capture());
        assertTrue(passwordEncoder.matches("correct-pass", inserted.getValue().passwordHash()));
        assertNotEquals("correct-pass", inserted.getValue().passwordHash());
        assertEquals("alice@example.test", result.user().email());
        assertEquals("USER", result.user().roles().get(0));
        assertFalse(result.accessToken().isBlank());
    }

    @Test
    void disabledUserCannotLogInOrCreateAuditEntry() {
        UserAccount disabled = account("DISABLED", "USER", passwordEncoder.encode("correct-pass"));
        when(userMapper.findByUsername("alice")).thenReturn(Optional.of(disabled));
        DefaultAuthService service = service();

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("alice", "correct-pass")));

        assertEquals("INVALID_CREDENTIALS", error.code());
        verify(auditService, never()).append(any());
    }

    @Test
    void successfulLoginAuditsAndUsesTheCurrentRole() {
        UserAccount admin = account("ACTIVE", "SYSTEM_ADMIN", passwordEncoder.encode("correct-pass"));
        when(userMapper.findByUsername("alice")).thenReturn(Optional.of(admin));
        DefaultAuthService service = service();

        var result = service.login(new LoginRequest("alice", "correct-pass"));

        assertEquals("SYSTEM_ADMIN", result.user().roles().get(0));
        assertEquals(admin.id().toString(), new JwtTokenService(JWT_PROPERTIES).parse(result.accessToken()).getSubject());
        verify(auditService).append(any());
    }

    private DefaultAuthService service() {
        return new DefaultAuthService(userMapper, passwordEncoder, new JwtTokenService(JWT_PROPERTIES), JWT_PROPERTIES, auditService);
    }

    private UserAccount account(String status, String role, String passwordHash) {
        Instant now = Instant.now();
        return new UserAccount(UUID.randomUUID(), "alice", "alice@example.test", passwordHash, role, status, now, now);
    }
}
