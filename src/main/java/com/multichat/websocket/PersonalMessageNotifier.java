package com.multichat.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multichat.message.entity.ChatMessage;
import com.multichat.common.logging.BusinessLogger;
import com.multichat.infrastructure.metrics.BusinessMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Sends review outcomes only to the submitting user's authenticated socket. */
@Component
public class PersonalMessageNotifier {
    private static final Logger log = LoggerFactory.getLogger(PersonalMessageNotifier.class);
    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final BusinessLogger businessLogger;
    private final BusinessMetrics businessMetrics;

    public PersonalMessageNotifier(WebSocketSessionRegistry sessionRegistry, ObjectMapper objectMapper) {
        this(sessionRegistry, objectMapper, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PersonalMessageNotifier(WebSocketSessionRegistry sessionRegistry, ObjectMapper objectMapper,
                                   BusinessLogger businessLogger, BusinessMetrics businessMetrics) {
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
        this.businessLogger = businessLogger;
        this.businessMetrics = businessMetrics;
    }

    public void notifyReviewResult(ChatMessage message, String reviewResult) {
        sessionRegistry.sessionForUser(message.senderId()).ifPresent(session -> {
            if (!session.isOpen()) return;
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("messageId", message.id().toString());
                payload.put("roomId", message.roomId().toString());
                payload.put("roomSeq", message.roomSeq() == null ? null : message.roomSeq().toString());
                payload.put("messageStatus", message.status());
                payload.put("reviewResult", reviewResult);
                payload.put("reviewDeadlineAt", message.reviewDeadlineAt());
                payload.put("reviewedAt", message.reviewedAt());
                payload.put("publishedAt", message.publishedAt());
                synchronized (session) {
                    if (session.isOpen()) session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                            "type", "REVIEW_STATUS",
                            "requestId", UUID.randomUUID().toString(),
                            "causationRequestId", message.requestId().toString(),
                            "occurredAt", Instant.now().toString(),
                            "payload", payload
                    ))));
                }
                logLifecycle("REVIEW_STATUS_PUSHED:" + reviewResult, message);
            } catch (IOException exception) {
                log.warn("Unable to notify user {} of review result for message {}", message.senderId(), message.id(), exception);
                if (businessMetrics != null) businessMetrics.recordPushFailure();
                logLifecycle("REVIEW_STATUS_PUSH_FAILED:" + reviewResult, message);
            }
        });
    }

    private void logLifecycle(String event, ChatMessage message) {
        if (businessLogger != null && message != null) {
            businessLogger.messageLifecycle(event, message.requestId(), message.id(), message.roomId(), message.senderId());
        }
    }
}
