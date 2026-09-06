package com.multichat.websocket;

import com.multichat.auth.entity.UserAccount;
import com.multichat.infrastructure.mapper.UserMapper;
import com.multichat.security.JwtTokenService;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    private final JwtTokenService jwtTokenService;
    private final UserMapper userMapper;

    public JwtHandshakeInterceptor(JwtTokenService jwtTokenService, UserMapper userMapper) {
        this.jwtTokenService = jwtTokenService;
        this.userMapper = userMapper;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Map<String, Object> attributes) {
        String token = bearerToken(request);
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            UUID userId = UUID.fromString(jwtTokenService.parse(token).getSubject());
            UserAccount user = userMapper.findActiveById(userId).orElse(null);
            if (user == null) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            attributes.put("userId", userId.toString());
            return true;
        } catch (JwtException | IllegalArgumentException ignored) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
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
