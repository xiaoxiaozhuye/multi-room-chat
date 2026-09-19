package com.multichat.auth.controller;

import com.multichat.auth.dto.AuthenticatedUserResponse;
import com.multichat.auth.dto.UpdateProfileRequest;
import com.multichat.auth.service.AuthService;
import com.multichat.common.api.ApiResponse;
import com.multichat.infrastructure.mapper.UserMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/profile")
public class ProfileController {
    private final UserMapper users;
    private final AuthService authService;
    public ProfileController(UserMapper users, AuthService authService) { this.users = users; this.authService = authService; }

    @PutMapping
    public ResponseEntity<ApiResponse<AuthenticatedUserResponse>> update(Authentication authentication,
                                                                           @Valid @RequestBody UpdateProfileRequest request) {
        UUID id = UUID.fromString(authentication.getName());
        var current = users.findActiveById(id).orElseThrow();
        users.updateProfile(new com.multichat.auth.entity.UserAccount(id, current.username(), current.email(), current.passwordHash(), current.role(), current.status(), current.createdAt(), current.updatedAt(),
                request.displayName().trim(), request.avatarUrl(), current.level(), request.bio() == null ? null : request.bio().trim()));
        return ResponseEntity.ok(ApiResponse.success(authService.currentUser(id)));
    }
}
