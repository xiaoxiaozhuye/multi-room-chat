package com.multichat.common.api;

import java.util.List;

/** A reusable error payload for transports, such as WebSocket ERROR frames. */
public record ApiError(String code, String message, List<ValidationError> details) {
    public ApiError(String code, String message) {
        this(code, message, List.of());
    }
}
