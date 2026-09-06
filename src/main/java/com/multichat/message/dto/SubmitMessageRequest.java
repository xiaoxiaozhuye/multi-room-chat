package com.multichat.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubmitMessageRequest(@NotNull UUID roomId, @NotBlank String content) {
}
