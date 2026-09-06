package com.multichat.message.service;

import com.multichat.common.exception.BusinessException;
import com.multichat.common.logging.BusinessLogger;
import com.multichat.common.logging.LogContext;
import com.multichat.audit.AuditStates;
import com.multichat.audit.entity.AuditLog;
import com.multichat.audit.service.AuditService;
import com.multichat.message.dto.SubmitMessageRequest;
import com.multichat.message.entity.ChatMessage;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Logs a message submission with the same four correlation fields throughout the
 * call. Future MessageService implementations receive this trace automatically.
 */
@Aspect
@Component
public class MessageLifecycleLoggingAspect {
    private final BusinessLogger businessLogger;
    private final AuditService auditService;

    public MessageLifecycleLoggingAspect(BusinessLogger businessLogger, AuditService auditService) {
        this.businessLogger = businessLogger;
        this.auditService = auditService;
    }

    @Around("execution(* com.multichat.message.service.MessageService.submit(..)) && args(senderId, requestId, request)")
    public Object logSubmission(ProceedingJoinPoint joinPoint, UUID senderId, UUID requestId,
                                SubmitMessageRequest request) throws Throwable {
        try (LogContext.Scope ignored = LogContext.scope(requestId.toString(), senderId, request.roomId(), null)) {
            businessLogger.messageLifecycle("SUBMIT_RECEIVED", null, request.roomId(), senderId);
            try {
                Object result = joinPoint.proceed();
                if (result instanceof ChatMessage message) {
                    LogContext.putMessageId(message.id());
                    businessLogger.messageLifecycle("SUBMIT_PERSISTED", message.id(), request.roomId(), senderId);
                    auditService.append(new AuditLog(UUID.randomUUID(), requestId, senderId, "MESSAGE_SUBMIT", "MESSAGE",
                            message.id(), message.roomId(), message.id(), null, AuditStates.message(message),
                            AuditStates.detail("messageType", message.messageType()), message.createdAt()));
                }
                return result;
            } catch (BusinessException exception) {
                businessLogger.messageLifecycle("SUBMIT_REJECTED:" + exception.code(), null, request.roomId(), senderId);
                throw exception;
            } catch (Throwable exception) {
                businessLogger.messageLifecycle("SUBMIT_FAILED", null, request.roomId(), senderId);
                throw exception;
            }
        }
    }
}
