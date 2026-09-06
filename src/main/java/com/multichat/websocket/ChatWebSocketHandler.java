package com.multichat.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multichat.common.api.ApiError;
import com.multichat.common.logging.BusinessLogger;
import com.multichat.common.logging.LogContext;
import com.multichat.infrastructure.mapper.UserMapper;
import com.multichat.infrastructure.mapper.RoomMembershipMapper;
import com.multichat.infrastructure.redis.OnlineStatusService;
import com.multichat.room.RoomStatePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketCommandValidator commandValidator;
    private final WebSocketExceptionMapper exceptionMapper;
    private final ObjectMapper objectMapper;
    private final BusinessLogger businessLogger;
    private final OnlineStatusService onlineStatusService;
    private final UserMapper userMapper;
    private final RoomStatePolicy roomStatePolicy;
    private final RoomMembershipMapper membershipMapper;

    public ChatWebSocketHandler(WebSocketSessionRegistry sessionRegistry, WebSocketCommandValidator commandValidator,
                                WebSocketExceptionMapper exceptionMapper, ObjectMapper objectMapper,
                                BusinessLogger businessLogger, OnlineStatusService onlineStatusService,
                                UserMapper userMapper, RoomStatePolicy roomStatePolicy,
                                RoomMembershipMapper membershipMapper) {
        this.sessionRegistry = sessionRegistry;
        this.commandValidator = commandValidator;
        this.exceptionMapper = exceptionMapper;
        this.objectMapper = objectMapper;
        this.businessLogger = businessLogger;
        this.onlineStatusService = onlineStatusService;
        this.userMapper = userMapper;
        this.roomStatePolicy = roomStatePolicy;
        this.membershipMapper = membershipMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String userId = (String) session.getAttributes().get("userId");
        if (!isActive(userId)) {
            session.close(new CloseStatus(4003, "ACCOUNT_DISABLED"));
            return;
        }
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
            String userId = (String) session.getAttributes().get("userId");
            if (!isActive(userId)) {
                session.close(new CloseStatus(4003, "ACCOUNT_DISABLED"));
                return;
            }
            WebSocketCommand command = commandValidator.validate(message.getPayload());
            commandType = command.type();
            requestId = command.requestId();
            JsonNode roomIdNode = command.payload().get("roomId");
            roomId = roomIdNode == null ? null : roomIdNode.asText();
            authorizeRoomCommand(command.type(), roomId, java.util.UUID.fromString(userId));
            onlineStatusService.refresh(java.util.UUID.fromString(userId));
            try (LogContext.Scope ignored = LogContext.scope(requestId.toString(), userId, roomId, null)) {
                businessLogger.event("websocket_command_accepted type=" + command.type());
                handleSubscriptionCommand(session, command.type(), requestId, roomId);
                // CHAT_SUBMIT domain dispatch is delegated to the message service module.
            }
        } catch (Exception exception) {
            log.warn("WebSocket command rejected", exception instanceof com.multichat.common.exception.BusinessException ? null : exception);
            sendError(session, requestId, commandType, roomId, exceptionMapper.toError(exception));
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        String userId = (String) session.getAttributes().get("userId");
        if (isActive(userId)) {
            onlineStatusService.refresh(java.util.UUID.fromString(userId));
        } else if (session.isOpen()) {
            try {
                session.close(new CloseStatus(4003, "ACCOUNT_DISABLED"));
            } catch (IOException exception) {
                log.debug("Unable to close disabled user's WebSocket session", exception);
            }
        }
    }

    private void sendError(WebSocketSession session, java.util.UUID requestId, String commandType, String roomId,
                           ApiError error) throws IOException {
        if (session.isOpen()) {
            WebSocketErrorEvent event = WebSocketErrorEvent.of(requestId, commandType, roomId, error);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
        }
    }

    private boolean isActive(String userId) {
        if (userId == null) return false;
        try {
            return userMapper.findActiveById(java.util.UUID.fromString(userId)).isPresent();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void authorizeRoomCommand(String commandType, String rawRoomId, java.util.UUID userId) {
        if (rawRoomId == null) return;
        java.util.UUID roomId = java.util.UUID.fromString(rawRoomId);
        if ("SUBSCRIBE_ROOM".equals(commandType)) {
            roomStatePolicy.requireSubscribable(roomId);
            if (membershipMapper.findActive(userId, roomId).isEmpty()) {
                throw new com.multichat.common.exception.BusinessException(com.multichat.common.exception.ErrorCode.ROOM_ACCESS_DENIED);
            }
        } else if ("CHAT_SUBMIT".equals(commandType)) {
            roomStatePolicy.requireUserChatAllowed(roomId);
            if (membershipMapper.findActive(userId, roomId).isEmpty()) {
                throw new com.multichat.common.exception.BusinessException(com.multichat.common.exception.ErrorCode.ROOM_ACCESS_DENIED);
            }
        }
    }

    private void handleSubscriptionCommand(WebSocketSession session, String commandType, java.util.UUID requestId,
                                           String rawRoomId) throws IOException {
        if (rawRoomId == null) return;
        java.util.UUID roomId = java.util.UUID.fromString(rawRoomId);
        String subscriptionStatus;
        if ("SUBSCRIBE_ROOM".equals(commandType)) {
            sessionRegistry.subscribe(session, roomId);
            subscriptionStatus = "SUBSCRIBED";
        } else if ("UNSUBSCRIBE_ROOM".equals(commandType)) {
            sessionRegistry.unsubscribe(session, roomId);
            subscriptionStatus = "UNSUBSCRIBED";
        } else {
            return;
        }
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", commandType,
                "requestId", requestId.toString(),
                "occurredAt", Instant.now().toString(),
                "payload", Map.of("roomId", rawRoomId, "subscriptionStatus", subscriptionStatus)
        ))));
    }
}
