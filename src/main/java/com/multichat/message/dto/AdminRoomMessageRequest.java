package com.multichat.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** Common request body for normal administrative broadcasts and emergencies. */
public record AdminRoomMessageRequest(
        @NotEmpty List<UUID> roomIds,
        @NotBlank String content) {
}
