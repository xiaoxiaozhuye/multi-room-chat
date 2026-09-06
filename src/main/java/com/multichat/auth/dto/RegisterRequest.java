package com.multichat.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Credentials and contact address required for a new account. */
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 32) @Pattern(regexp = "[A-Za-z0-9_-]+") String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {
}
