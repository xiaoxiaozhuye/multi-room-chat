package com.multichat.websocket;

import com.multichat.security.JwtTokenService;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    private final JwtTokenService jwtTokenService;

    public JwtHandshakeInterceptor(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Map<String, Object> attributes) {
        String token = bearerToken(request);
        if (token == null) {
            return false;
        }
        try {
            attributes.put("userId", jwtTokenService.parse(token).getSubject());
            return true;
        } catch (JwtException | IllegalArgumentException ignored) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                               Exception exception) {
        // No per-connection cleanup is required before a session has been established.
    }

    private String bearerToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        String query = request.getURI().getRawQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && parts[0].equals("token")) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
