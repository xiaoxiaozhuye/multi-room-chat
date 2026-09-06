package com.multichat.auth.service;

import com.multichat.auth.dto.LoginRequest;

public interface AuthService {
    /** Authenticate credentials and issue an access/refresh token pair. */
    void login(LoginRequest request);
}
