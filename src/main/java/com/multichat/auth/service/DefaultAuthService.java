package com.multichat.auth.service;

import com.multichat.auth.dto.LoginRequest;
import com.multichat.auth.dto.RegisterRequest;
import com.multichat.auth.dto.AuthenticationResponse;
import com.multichat.auth.dto.AuthenticatedUserResponse;
import com.multichat.auth.entity.UserAccount;
import com.multichat.audit.entity.AuditLog;
import com.multichat.audit.AuditStates;
import com.multichat.audit.service.AuditService;
import com.multichat.common.exception.BusinessException;
import com.multichat.common.web.RequestIdFilter;
import com.multichat.infrastructure.mapper.UserMapper;
import com.multichat.security.JwtProperties;
import com.multichat.security.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps password verification, account status checks and token issuance in one
 * place, while transport-specific authentication stays in the security layer.
 */
@Service
public class DefaultAuthService implements AuthService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final AuditService auditService;

    public DefaultAuthService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                              JwtTokenService jwtTokenService, JwtProperties jwtProperties,
                              AuditService auditService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
        this.auditService = auditService;
    }

    @Override
    public AuthenticationResponse register(RegisterRequest request) {
        Instant now = Instant.now();
        UserAccount user = new UserAccount(UUID.randomUUID(), request.username().trim(), request.email().trim().toLowerCase(),
                passwordEncoder.encode(request.password()), "USER", "ACTIVE", now, now);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw usernameAlreadyExists();
        }
        return authenticationResponse(user);
    }

    @Override
    public AuthenticationResponse login(LoginRequest request) {
        UserAccount user = userMapper.findByUsername(request.username().trim()).orElseThrow(this::invalidCredentials);
        if (!"ACTIVE".equals(user.status()) || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw invalidCredentials();
        }
        Instant now = Instant.now();
        auditService.append(new AuditLog(UUID.randomUUID(), requestId(), user.id(), "LOGIN", "USER", user.id(),
                null, null, Map.of(), AuditStates.detail("userId", user.id(), "status", user.status()),
                AuditStates.detail("authentication", "PASSWORD"), now));
        return authenticationResponse(user);
    }

    @Override
    public AuthenticatedUserResponse currentUser(UUID userId) {
        UserAccount user = userMapper.findActiveById(userId)
                .orElseThrow(() -> new BusinessException("UNAUTHENTICATED", "Authentication is required.", HttpStatus.UNAUTHORIZED));
        return userResponse(user);
    }

    private AuthenticationResponse authenticationResponse(UserAccount user) {
        Instant expiresAt = Instant.now().plus(jwtProperties.accessTokenTtl());
        return new AuthenticationResponse(userResponse(user), jwtTokenService.createAccessToken(user.id(), List.of(user.role())), expiresAt);
    }

    private AuthenticatedUserResponse userResponse(UserAccount user) {
        return new AuthenticatedUserResponse(user.id(), user.username(), user.email(), List.of(user.role()), user.createdAt());
    }

    private BusinessException invalidCredentials() {
        return new BusinessException("INVALID_CREDENTIALS", "Invalid username or password.", HttpStatus.UNAUTHORIZED);
    }

    private BusinessException usernameAlreadyExists() {
        return new BusinessException("USERNAME_ALREADY_EXISTS", "Username is already in use.", HttpStatus.CONFLICT);
    }

    private UUID requestId() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            Object requestId = request.getAttribute(RequestIdFilter.HEADER);
            if (requestId instanceof String value) {
                try {
                    return UUID.fromString(value);
                } catch (IllegalArgumentException ignored) {
                    // The request-id filter has already validated external values.
                }
            }
        }
        return null;
    }
}
