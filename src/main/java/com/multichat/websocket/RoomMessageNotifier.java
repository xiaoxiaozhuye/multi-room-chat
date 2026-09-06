package com.multichat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multichat.auth.entity.UserAccount;
import com.multichat.common.api.ApiError;
import com.multichat.common.exception.BusinessException;
import com.multichat.common.logging.BusinessLogger;
import com.multichat.infrastructure.metrics.BusinessMetrics;
import com.multichat.infrastructure.mapper.UserMapper;
import com.multichat.message.entity.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Delivers published room events only to current, still-authorized subscribers. */
@Component
public class RoomMessageNotifier {
    private static final Logger log = LoggerFactory.getLogger(RoomMessageNotifier.class);
    private final WebSocketSessionRegistry sessionRegistry;
    private final RoomSubscriptionAuthorizer authorizer;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final WebSocketExceptionMapper exceptionMapper;
    private final BusinessLogger businessLogger;
    private final BusinessMetrics businessMetrics;

    public RoomMessageNotifier(WebSocketSessionRegistry sessionRegistry, RoomSubscriptionAuthorizer authorizer,
                               UserMapper userMapper, ObjectMapper objectMapper,
                               WebSocketExceptionMapper exceptionMapper) {
        this(sessionRegistry, authorizer, userMapper, objectMapper, exceptionMapper, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RoomMessageNotifier(WebSocketSessionRegistry sessionRegistry, RoomSubscriptionAuthorizer authorizer,
                               UserMapper userMapper, ObjectMapper objectMapper,
                               WebSocketExceptionMapper exceptionMapper, BusinessLogger businessLogger,
                               BusinessMetrics businessMetrics) {
        this.sessionRegistry = sessionRegistry;
        this.authorizer = authorizer;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
        this.exceptionMapper = exceptionMapper;
        this.businessLogger = businessLogger;
        this.businessMetrics = businessMetrics;
    }

    /** Emits CHAT_MESSAGE or NOTIFICATION according to the persisted message type. */
    public void notifyPublished(ChatMessage message) {
        if (message == null || !"PUBLISHED".equals(message.status())) return;
        deliverToSubscribers(message, message.status(), message.publishedAt());
    }

    /**
     * Attempts a normal-message delivery before its database state is changed.
     * A false result leaves the authoritative row APPROVED so the publisher can
     * retry it later.  Recipients may see a duplicate when a socket fails after
     * accepting bytes; the stable messageId is the client deduplication key.
     */
    public boolean publishApproved(ChatMessage message, Instant publishedAt) {
        if (message == null || !"APPROVED".equals(message.status()) || publishedAt == null) return false;
        return deliverToSubscribers(message, "PUBLISHED", publishedAt);
    }

    private boolean deliverToSubscribers(ChatMessage message, String eventStatus, Instant eventPublishedAt) {
        String type = "SYSTEM_NOTIFICATION".equals(message.messageType()) ? "NOTIFICATION" : "CHAT_MESSAGE";
        String senderDisplayName = userMapper.findActiveById(message.senderId()).map(UserAccount::username).orElse(null);
        boolean delivered = true;
        for (WebSocketSession session : sessionRegistry.sessionsForRoom(message.roomId())) {
            String rawUserId = sessionRegistry.userIdFor(session).orElse(null);
            try {
                if (rawUserId == null) continue;
                authorizer.requireSubscribable(UUID.fromString(rawUserId), message.roomId());
                if (!send(session, event(type, message, senderDisplayName, eventStatus, eventPublishedAt))) {
                    delivered = false;
                    recordPushFailure(message, rawUserId, "PUSH_FAILED");
                } else {
                    logPush(message, rawUserId, "PUSH_DELIVERED");
                }
            } catch (BusinessException exception) {
                sessionRegistry.unsubscribe(session, message.roomId());
                sendRevoked(session, message.roomId(), exceptionMapper.toError(exception));
            } catch (RuntimeException exception) {
                log.warn("Unable to validate WebSocket recipient {}", rawUserId, exception);
                delivered = false;
                recordPushFailure(message, rawUserId, "PUSH_RECIPIENT_VALIDATION_FAILED");
            }
        }
        return delivered;
    }

    /** Explicit entry point for the committed SYSTEM_NOTIFICATION publish path. */
    public void notifyEmergency(ChatMessage message) {
        publishEmergency(message);
    }

    /**
     * Returns whether the immediate at-least-once delivery completed.  The
     * caller keeps the durable emergency compensation row whenever this is
     * false; the already-published message itself is never rolled back.
     */
    public boolean publishEmergency(ChatMessage message) {
        if (message == null || !"SYSTEM_NOTIFICATION".equals(message.messageType())
                || !"PUBLISHED".equals(message.status())) return false;
        return deliverToSubscribers(message, message.status(), message.publishedAt());
    }

    /** Sends one persisted event to a known subscribed session during replay. */
    public void deliver(WebSocketSession session, ChatMessage message) {
        if (message == null || !"PUBLISHED".equals(message.status())) return;
        String type = "SYSTEM_NOTIFICATION".equals(message.messageType()) ? "NOTIFICATION" : "CHAT_MESSAGE";
        String displayName = userMapper.findActiveById(message.senderId()).map(UserAccount::username).orElse(null);
        String recipientId = sessionRegistry.userIdFor(session).orElse(null);
        if (!send(session, event(type, message, displayName, message.status(), message.publishedAt()))) {
            recordPushFailure(message, recipientId, "PUSH_REPLAY_FAILED");
        } else {
            logPush(message, recipientId, "PUSH_REPLAY_DELIVERED");
        }
    }

    public void revokeUser(UUID userId, UUID roomId, ApiError error) {
        sessionRegistry.unsubscribeUserFromRoom(userId, roomId).ifPresent(session -> sendRevoked(session, roomId, error));
    }

    public void revokeRoom(UUID roomId, ApiError error) {
        sessionRegistry.unsubscribeRoom(roomId).forEach(session -> sendRevoked(session, roomId, error));
    }

    private Map<String, Object> event(String type, ChatMessage message, String senderDisplayName,
                                      String messageStatus, Instant publishedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", message.id().toString());
        payload.put("roomId", message.roomId().toString());
        payload.put("roomSeq", asString(message.roomSeq()));
        payload.put("notificationSeq", asString(message.notificationSeq()));
        payload.put("senderId", message.senderId().toString());
        if (senderDisplayName != null && "CHAT_MESSAGE".equals(type)) payload.put("senderDisplayName", senderDisplayName);
        payload.put("messageType", message.messageType());
        payload.put("content", message.content());
        payload.put("messageStatus", messageStatus);
        payload.put("createdAt", message.createdAt());
        payload.put("publishedAt", publishedAt);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("requestId", UUID.randomUUID().toString());
        event.put("occurredAt", Instant.now());
        event.put("payload", payload);
        return event;
    }

    private void sendRevoked(WebSocketSession session, UUID roomId, ApiError error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", error.code());
        payload.put("message", error.message());
        payload.put("commandType", "SUBSCRIBE_ROOM");
        payload.put("roomId", roomId.toString());
        payload.put("details", error.details());
        send(session, Map.of("type", "ERROR", "requestId", UUID.randomUUID().toString(),
                "occurredAt", Instant.now(), "payload", payload));
    }

    private boolean send(WebSocketSession session, Map<String, Object> event) {
        if (!session.isOpen()) return true;
        try {
            synchronized (session) {
                if (session.isOpen()) session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
            }
            return true;
        } catch (IOException exception) {
            log.debug("WebSocket delivery failed for session {}", session.getId(), exception);
            return false;
        }
    }

    private String asString(Long value) {
        return value == null ? null : value.toString();
    }

    private void recordPushFailure(ChatMessage message, String recipientId, String event) {
        if (businessMetrics != null) businessMetrics.recordPushFailure();
        logPush(message, recipientId, event);
    }

    private void logPush(ChatMessage message, String recipientId, String event) {
        if (businessLogger != null && message != null) {
            businessLogger.messageLifecycle(event + " recipientId=" + (recipientId == null ? "unknown" : recipientId),
                    message.requestId(), message.id(), message.roomId(), recipientId);
        }
    }
}
