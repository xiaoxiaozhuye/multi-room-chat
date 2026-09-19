package com.multichat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(@NotBlank @Size(max = 64) String displayName,
                                   @Size(max = 2_000_000) String avatarUrl,
                                   @Size(max = 240) String bio) {}
