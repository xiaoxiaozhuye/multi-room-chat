package com.multichat.auth.controller;

import com.multichat.auth.dto.AuthenticatedUserResponse;
import com.multichat.auth.service.AuthService;
import com.multichat.common.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class CurrentUserController {
    private final AuthService authService;

    public CurrentUserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthenticatedUserResponse>> currentUser(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(authService.currentUser(UUID.fromString(authentication.getName()))));
    }
}
