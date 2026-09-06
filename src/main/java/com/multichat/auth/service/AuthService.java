package com.multichat.auth.service;

import com.multichat.auth.dto.AuthenticationResponse;
import com.multichat.auth.dto.AuthenticatedUserResponse;
import com.multichat.auth.dto.LoginRequest;
import com.multichat.auth.dto.RegisterRequest;

import java.util.UUID;

public interface AuthService {
    AuthenticationResponse register(RegisterRequest request);

    /** Authenticate credentials and issue a short-lived access token. */
    AuthenticationResponse login(LoginRequest request);

    AuthenticatedUserResponse currentUser(UUID userId);
}
