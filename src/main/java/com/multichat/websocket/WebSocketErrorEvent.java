package com.multichat.websocket;

import com.multichat.common.api.ApiError;

import java.time.Instant;
import java.util.UUID;

/** Stable ERROR event emitted for a rejected WebSocket command. */
public record WebSocketErrorEvent(String type, String requestId, Instant occurredAt, Payload payload) {
    public record Payload(String code, String message, String commandType, String roomId,
                          java.util.List<com.multichat.common.api.ValidationError> details) {
    }

    public static WebSocketErrorEvent of(UUID requestId, String commandType, String roomId, ApiError error) {
        return new WebSocketErrorEvent("ERROR", requestId == null ? UUID.randomUUID().toString() : requestId.toString(),
                Instant.now(), new Payload(error.code(), error.message(), commandType, roomId, error.details()));
    }
}
