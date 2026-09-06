package com.multichat.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multichat.common.api.ApiError;
import com.multichat.common.logging.BusinessLogger;
import com.multichat.common.logging.LogContext;
import com.multichat.infrastructure.redis.OnlineStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketCommandValidator commandValidator;
    private final WebSocketExceptionMapper exceptionMapper;
    private final ObjectMapper objectMapper;
    private final BusinessLogger businessLogger;
    private final OnlineStatusService onlineStatusService;

    public ChatWebSocketHandler(WebSocketSessionRegistry sessionRegistry, WebSocketCommandValidator commandValidator,
                                WebSocketExceptionMapper exceptionMapper, ObjectMapper objectMapper,
                                BusinessLogger businessLogger, OnlineStatusService onlineStatusService) {
        this.sessionRegistry = sessionRegistry;
        this.commandValidator = commandValidator;
        this.exceptionMapper = exceptionMapper;
        this.objectMapper = objectMapper;
        this.businessLogger = businessLogger;
        this.onlineStatusService = onlineStatusService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String userId = (String) session.getAttributes().get("userId");
        sessionRegistry.register(userId, session);
        onlineStatusService.markOnline(java.util.UUID.fromString(userId));
        session.sendMessage(new TextMessage("{\"type\":\"CONNECTED\"}"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            if (sessionRegistry.unregister(userId, session)) {
                onlineStatusService.markOffline(java.util.UUID.fromString(userId));
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String commandType = null;
        String roomId = null;
        java.util.UUID requestId = null;
        try {
            WebSocketCommand command = commandValidator.validate(message.getPayload());
            commandType = command.type();
            requestId = command.requestId();
            JsonNode roomIdNode = command.payload().get("roomId");
            roomId = roomIdNode == null ? null : roomIdNode.asText();
            String userId = (String) session.getAttributes().get("userId");
            onlineStatusService.refresh(java.util.UUID.fromString(userId));
            try (LogContext.Scope ignored = LogContext.scope(requestId.toString(), userId, roomId, null)) {
                businessLogger.event("websocket_command_accepted type=" + command.type());
                // Domain command dispatch is intentionally delegated to the corresponding service modules.
            }
        } catch (Exception exception) {
            log.warn("WebSocket command rejected", exception instanceof com.multichat.common.exception.BusinessException ? null : exception);
            sendError(session, requestId, commandType, roomId, exceptionMapper.toError(exception));
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            onlineStatusService.refresh(java.util.UUID.fromString(userId));
        }
    }

    private void sendError(WebSocketSession session, java.util.UUID requestId, String commandType, String roomId,
                           ApiError error) throws IOException {
        if (session.isOpen()) {
            WebSocketErrorEvent event = WebSocketErrorEvent.of(requestId, commandType, roomId, error);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
        }
    }
}
