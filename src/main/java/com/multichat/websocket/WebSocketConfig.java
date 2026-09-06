package com.multichat.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final ChatWebSocketHandler handler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final WebSocketProperties properties;

    public WebSocketConfig(ChatWebSocketHandler handler, JwtHandshakeInterceptor jwtHandshakeInterceptor,
                           WebSocketProperties properties) {
        this.handler = handler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/v1/chat")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns(properties.allowedOrigins().toArray(String[]::new));
    }
}
