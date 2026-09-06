package com.multichat.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SubmitMessageRequest(@NotNull UUID roomId, @NotBlank @Size(max = 320) String content) {
}
