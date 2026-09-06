package com.multichat.websocket;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/** A syntactically and structurally valid client WebSocket command. */
public record WebSocketCommand(String type, UUID requestId, JsonNode payload) {
}
