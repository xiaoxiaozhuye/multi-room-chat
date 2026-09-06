package com.multichat.room.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateRoomRequest(@NotBlank String name, String description,
                                @Min(1) @Max(100000) int maxMembers, @NotBlank String joinMode) {
}
