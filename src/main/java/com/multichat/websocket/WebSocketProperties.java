package com.multichat.websocket;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "chat.websocket")
public record WebSocketProperties(List<String> allowedOrigins) {
}
