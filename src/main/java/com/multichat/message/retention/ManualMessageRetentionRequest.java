package com.multichat.message.retention;

import jakarta.validation.constraints.NotBlank;

/** A deliberately non-guessable acknowledgement prevents accidental manual purges. */
public record ManualMessageRetentionRequest(@NotBlank String confirmation) {
    public static final String REQUIRED_CONFIRMATION = "PURGE_EXPIRED_MESSAGE_BODIES";
}
