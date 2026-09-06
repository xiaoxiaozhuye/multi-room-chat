package com.multichat.auth.service;

import com.multichat.auth.dto.LoginRequest;
import com.multichat.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Authentication use cases are intentionally introduced behind this boundary.
 * Token issuance and password verification are implemented in the auth task,
 * while security transport is already available through JwtTokenService.
 */
@Service
public class DefaultAuthService implements AuthService {
    @Override
    public void login(LoginRequest request) {
        throw new BusinessException("NOT_IMPLEMENTED", "Authentication use case is not implemented yet", HttpStatus.NOT_IMPLEMENTED);
    }
}
