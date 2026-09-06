package com.multichat.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multichat.common.api.ApiError;
import com.multichat.common.logging.BusinessLogger;
import com.multichat.common.logging.LogContext;
import com.multichat.infrastructure.mapper.UserMapper;
import com.multichat.infrastructure.mapper.RoomMembershipMapper;
import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.infrastructure.redis.OnlineStatusService;
import com.multichat.message.dto.SubmitMessageRequest;
import com.multichat.message.entity.ChatMessage;
import com.multichat.message.service.MessageService;
import com.multichat.room.RoomStatePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.time.Duration;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
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
    private final MessageService messageService;
    private final RoomSubscriptionAuthorizer subscriptionAuthorizer;
    private final MessageMapper messageMapper;
    private final RoomMessageNotifier roomMessageNotifier;
    private final Duration heartbeatTimeout;

    /** Retained for focused handler tests that do not wire the publication/replay collaborators. */
    public ChatWebSocketHandler(WebSocketSessionRegistry sessionRegistry, WebSocketCommandValidator commandValidator,
                                WebSocketExceptionMapper exceptionMapper, ObjectMapper objectMapper,
                                BusinessLogger businessLogger, OnlineStatusService onlineStatusService,
                                UserMapper userMapper, RoomStatePolicy roomStatePolicy,
                                RoomMembershipMapper membershipMapper, MessageService messageService) {
        this(sessionRegistry, commandValidator, exceptionMapper, objectMapper, businessLogger, onlineStatusService,
                userMapper, roomStatePolicy, membershipMapper, messageService, null, null, null, Duration.ofSeconds(75));
    }

    @Autowired
    public ChatWebSocketHandler(WebSocketSessionRegistry sessionRegistry, WebSocketCommandValidator commandValidator,
                                WebSocketExceptionMapper exceptionMapper, ObjectMapper objectMapper,
                                BusinessLogger businessLogger, OnlineStatusService onlineStatusService,
                                UserMapper userMapper, RoomStatePolicy roomStatePolicy,
                                RoomMembershipMapper membershipMapper, MessageService messageService,
                                RoomSubscriptionAuthorizer subscriptionAuthorizer,
                                MessageMapper messageMapper, RoomMessageNotifier roomMessageNotifier,
                                @Value("${chat.websocket.heartbeat-timeout:PT75S}") Duration heartbeatTimeout) {
        this.sessionRegistry = sessionRegistry;
        this.commandValidator = commandValidator;
        this.exceptionMapper = exceptionMapper;
        this.objectMapper = objectMapper;
        this.businessLogger = businessLogger;
        this.onlineStatusService = onlineStatusService;
        this.userMapper = userMapper;
        this.roomStatePolicy = roomStatePolicy;
        this.membershipMapper = membershipMapper;
        this.messageService = messageService;
        this.subscriptionAuthorizer = subscriptionAuthorizer;
        this.messageMapper = messageMapper;
        this.roomMessageNotifier = roomMessageNotifier;
        this.heartbeatTimeout = heartbeatTimeout;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String userId = (String) session.getAttributes().get("userId");
        if (!isActive(userId)) {
            session.close(new CloseStatus(4003, "ACCOUNT_DISABLED"));
            return;
        }
        var replaced = sessionRegistry.register(userId, session);
        onlineStatusService.markOnline(java.util.UUID.fromString(userId));
        session.sendMessage(new TextMessage("{\"type\":\"CONNECTED\"}"));
        replaced.ifPresent(previous -> replace(previous));
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
            onlineStatusService.refresh(java.util.UUID.fromString(userId));
            try (LogContext.Scope ignored = LogContext.scope(requestId.toString(), userId, roomId, null)) {
                businessLogger.event("websocket_command_accepted type=" + command.type());
                if ("CHAT_SUBMIT".equals(command.type())) {
                    SubmitMessageRequest submit = objectMapper.treeToValue(command.payload(), SubmitMessageRequest.class);
                    ChatMessage submitted = messageService.submit(java.util.UUID.fromString(userId), requestId, submit);
                    sendReviewStatus(session, requestId, submitted);
                } else {
                    authorizeRoomCommand(command.type(), roomId, java.util.UUID.fromString(userId));
                    handleSubscriptionCommand(session, command.type(), requestId, roomId, command.payload());
                }
            }
        } catch (Exception exception) {
            log.warn("WebSocket command rejected", exception instanceof com.multichat.common.exception.BusinessException ? null : exception);
            sendError(session, requestId, commandType, roomId, exceptionMapper.toError(exception));
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        sessionRegistry.recordPong(session);
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
            if (subscriptionAuthorizer != null) {
                subscriptionAuthorizer.requireSubscribable(userId, roomId);
            } else {
                roomStatePolicy.requireSubscribable(roomId);
                if (membershipMapper.findActive(userId, roomId).isEmpty()) {
                    throw new com.multichat.common.exception.BusinessException(com.multichat.common.exception.ErrorCode.ROOM_ACCESS_DENIED);
                }
            }
        } else if ("CHAT_SUBMIT".equals(commandType)) {
            roomStatePolicy.requireUserChatAllowed(roomId);
            if (membershipMapper.findActive(userId, roomId).isEmpty()) {
                throw new com.multichat.common.exception.BusinessException(com.multichat.common.exception.ErrorCode.ROOM_ACCESS_DENIED);
            }
        }
    }

    private void handleSubscriptionCommand(WebSocketSession session, String commandType, java.util.UUID requestId,
                                           String rawRoomId, JsonNode payload) throws IOException {
        if (rawRoomId == null) return;
        java.util.UUID roomId = java.util.UUID.fromString(rawRoomId);
        String subscriptionStatus;
        if ("SUBSCRIBE_ROOM".equals(commandType)) {
            if (messageMapper == null || roomMessageNotifier == null) {
                sessionRegistry.subscribe(session, roomId);
                sendSubscriptionStatus(session, commandType, requestId, rawRoomId, "SUBSCRIBED", Map.of(
                        "messageReplayStatus", "COMPLETE", "notificationReplayStatus", "COMPLETE",
                        "replayToMessageSeq", "0", "replayToNotificationSeq", "0"));
                return;
            }
            long lastMessageSeq = requestedSequence(payload, "lastMessageSeq");
            long lastNotificationSeq = requestedSequence(payload, "lastNotificationSeq");
            // Register before reading the high-water mark. A concurrently published
            // event may therefore be duplicated by replay, but can never fall into a
            // subscribe/replay gap; clients deduplicate on messageId by contract.
            sessionRegistry.subscribe(session, roomId);
            long replayToMessageSeq = messageMapper.highestPublishedRoomSeq(roomId);
            long replayToNotificationSeq = messageMapper.highestPublishedNotificationSeq(roomId);
            if (lastMessageSeq > replayToMessageSeq || lastNotificationSeq > replayToNotificationSeq) {
                sessionRegistry.unsubscribe(session, roomId);
                throw new com.multichat.common.exception.BusinessException(com.multichat.common.exception.ErrorCode.VALIDATION_FAILED);
            }
            boolean messageGap = messageMapper.hasRetiredChatInRange(roomId, lastMessageSeq, replayToMessageSeq);
            boolean notificationGap = messageMapper.hasRetiredNotificationInRange(
                    roomId, lastNotificationSeq, replayToNotificationSeq);
            subscriptionStatus = messageGap || notificationGap ? "SUBSCRIBED_WITH_GAP" : "SUBSCRIBED";
            Map<String, Object> replay = new LinkedHashMap<>();
            replay.put("messageReplayStatus", messageGap ? "GAP" : "COMPLETE");
            replay.put("notificationReplayStatus", notificationGap ? "GAP" : "COMPLETE");
            replay.put("replayToMessageSeq", Long.toString(replayToMessageSeq));
            replay.put("replayToNotificationSeq", Long.toString(replayToNotificationSeq));
            if (messageGap) replay.put("earliestAvailableMessageSeq", earliestAvailable(messageMapper.earliestRetainedRoomSeq(roomId)));
            if (notificationGap) replay.put("earliestAvailableNotificationSeq",
                    earliestAvailable(messageMapper.earliestRetainedNotificationSeq(roomId)));
            sendSubscriptionStatus(session, commandType, requestId, rawRoomId, subscriptionStatus, replay);
            messageMapper.findPublishedChatRange(roomId, lastMessageSeq, replayToMessageSeq)
                    .forEach(message -> roomMessageNotifier.deliver(session, message));
            messageMapper.findPublishedNotificationRange(roomId, lastNotificationSeq, replayToNotificationSeq)
                    .forEach(message -> roomMessageNotifier.deliver(session, message));
            return;
        } else if ("UNSUBSCRIBE_ROOM".equals(commandType)) {
            sessionRegistry.unsubscribe(session, roomId);
            subscriptionStatus = "UNSUBSCRIBED";
        } else {
            return;
        }
        sendSubscriptionStatus(session, commandType, requestId, rawRoomId, subscriptionStatus, null);
    }

    private void sendSubscriptionStatus(WebSocketSession session, String commandType, java.util.UUID requestId,
                                        String rawRoomId, String subscriptionStatus, Map<String, Object> replay)
            throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomId", rawRoomId);
        payload.put("subscriptionStatus", subscriptionStatus);
        if (replay != null) payload.put("replay", replay);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", commandType,
                "requestId", requestId.toString(),
                "occurredAt", Instant.now().toString(),
                "payload", payload
        ))));
    }

    private long requestedSequence(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null) return 0L;
        try {
            return Long.parseLong(value.textValue());
        } catch (NumberFormatException exception) {
            throw new com.multichat.common.exception.BusinessException(
                    com.multichat.common.exception.ErrorCode.VALIDATION_FAILED);
        }
    }

    private String earliestAvailable(Long sequence) {
        // "0" unambiguously tells a client that no retained body remains in
        // this channel, while preserving the original high-water sequence.
        return sequence == null ? "0" : sequence.toString();
    }

    private void sendReviewStatus(WebSocketSession session, java.util.UUID causationRequestId, ChatMessage message)
            throws IOException {
        if (!session.isOpen()) return;
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "REVIEW_STATUS",
                "requestId", java.util.UUID.randomUUID().toString(),
                "causationRequestId", causationRequestId.toString(),
                "occurredAt", Instant.now().toString(),
                "payload", Map.of(
                        "messageId", message.id().toString(),
                        "roomId", message.roomId().toString(),
                        "roomSeq", Long.toString(message.roomSeq()),
                        "messageStatus", message.status(),
                        "reviewDeadlineAt", message.reviewDeadlineAt().toString()
                )
        ))));
    }

    /** Standard WebSocket Ping/Pong is the liveness source for node-local sessions. */
    @Scheduled(fixedDelayString = "${chat.websocket.heartbeat-interval:PT25S}")
    public void heartbeat() {
        Instant cutoff = Instant.now().minus(heartbeatTimeout);
        for (WebSocketSession session : sessionRegistry.sessions()) {
            Instant lastPong = sessionRegistry.lastPongAt(session);
            if (!session.isOpen() || lastPong == null || lastPong.isBefore(cutoff)) {
                disconnectStale(session);
                continue;
            }
            try {
                synchronized (session) {
                    if (session.isOpen()) session.sendMessage(new PingMessage(ByteBuffer.allocate(0)));
                }
            } catch (IOException exception) {
                disconnectStale(session);
            }
        }
    }

    private void replace(WebSocketSession previous) {
        try {
            if (previous.isOpen()) {
                sendSessionReplaced(previous);
                previous.close(new CloseStatus(4001, "SESSION_REPLACED"));
            }
        } catch (IOException exception) {
            log.debug("Unable to close replaced WebSocket session {}", previous.getId(), exception);
        }
    }

    private void sendSessionReplaced(WebSocketSession session) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", "SESSION_REPLACED");
        payload.put("message", "This connection was replaced by a newer connection.");
        payload.put("commandType", null);
        payload.put("roomId", null);
        payload.put("details", java.util.List.of());
        synchronized (session) {
            if (session.isOpen()) session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "ERROR", "requestId", java.util.UUID.randomUUID().toString(),
                    "occurredAt", Instant.now(), "payload", payload))));
        }
    }

    private void disconnectStale(WebSocketSession session) {
        String userId = sessionRegistry.userIdFor(session).orElse((String) session.getAttributes().get("userId"));
        if (userId != null && sessionRegistry.unregister(userId, session)) {
            onlineStatusService.markOffline(java.util.UUID.fromString(userId));
        }
        try {
            if (session.isOpen()) session.close(new CloseStatus(4000, "HEARTBEAT_TIMEOUT"));
        } catch (IOException exception) {
            log.debug("Unable to close stale WebSocket session {}", session.getId(), exception);
        }
    }
}
