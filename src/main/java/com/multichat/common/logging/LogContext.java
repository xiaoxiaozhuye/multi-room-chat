package com.multichat.common.logging;

import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralizes the MDC keys used by request and message lifecycle logs. Keeping the
 * keys fixed makes searching one message across HTTP, WebSocket and service logs
 * reliable.
 */
public final class LogContext {
    public static final String REQUEST_ID = "requestId";
    public static final String USER_ID = "userId";
    public static final String ROOM_ID = "roomId";
    public static final String MESSAGE_ID = "messageId";

    private LogContext() {
    }

    public static void putRequestId(String requestId) {
        put(REQUEST_ID, requestId);
    }

    public static void putUserId(Object userId) {
        put(USER_ID, userId);
    }

    public static void putRoomId(Object roomId) {
        put(ROOM_ID, roomId);
    }

    public static void putMessageId(Object messageId) {
        put(MESSAGE_ID, messageId);
    }

    public static void clearRequestContext() {
        MDC.remove(REQUEST_ID);
        MDC.remove(USER_ID);
        MDC.remove(ROOM_ID);
        MDC.remove(MESSAGE_ID);
    }

    public static Scope scope(String requestId, Object userId, Object roomId, Object messageId) {
        Map<String, String> previous = new LinkedHashMap<>();
        remember(previous, REQUEST_ID);
        remember(previous, USER_ID);
        remember(previous, ROOM_ID);
        remember(previous, MESSAGE_ID);
        putRequestId(requestId);
        putUserId(userId);
        putRoomId(roomId);
        putMessageId(messageId);
        return new Scope(previous);
    }

    private static void remember(Map<String, String> values, String key) {
        values.put(key, MDC.get(key));
    }

    private static void put(String key, Object value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, String.valueOf(value));
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Map<String, String> previous;

        private Scope(Map<String, String> previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            previous.forEach(LogContext::put);
        }
    }
}
