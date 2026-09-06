package com.multichat.common.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Emits structured, correlation-aware milestones for business workflows. */
@Component
public class BusinessLogger {
    private static final Logger log = LoggerFactory.getLogger(BusinessLogger.class);

    public void messageLifecycle(String event, Object messageId, Object roomId, Object userId) {
        messageLifecycle(event, org.slf4j.MDC.get(LogContext.REQUEST_ID), messageId, roomId, userId);
    }

    public void messageLifecycle(String event, Object requestId, Object messageId, Object roomId, Object userId) {
        try (LogContext.Scope ignored = LogContext.scope(
                requestId == null ? null : String.valueOf(requestId), userId, roomId, messageId)) {
            log.info("message_lifecycle event={}", event);
        }
    }

    public void event(String event) {
        log.info("business_event event={}", event);
    }
}
