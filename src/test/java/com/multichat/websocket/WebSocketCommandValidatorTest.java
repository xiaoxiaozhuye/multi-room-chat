package com.multichat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multichat.common.exception.BusinessException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSocketCommandValidatorTest {
    private final WebSocketCommandValidator validator = new WebSocketCommandValidator(
            new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void acceptsAWellFormedChatSubmit() {
        UUID requestId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        WebSocketCommand command = validator.validate("""
                {"type":"CHAT_SUBMIT","requestId":"%s","payload":{"roomId":"%s","content":"hello"}}
                """.formatted(requestId, roomId));

        assertEquals("CHAT_SUBMIT", command.type());
        assertEquals(requestId, command.requestId());
    }

    @Test
    void mapsMalformedAndInvalidFramesToValidationFailures() {
        BusinessException malformed = assertThrows(BusinessException.class, () -> validator.validate("{"));
        BusinessException invalidContent = assertThrows(BusinessException.class, () -> validator.validate("""
                {"type":"CHAT_SUBMIT","requestId":"%s","payload":{"roomId":"%s","content":"  "}}
                """.formatted(UUID.randomUUID(), UUID.randomUUID())));

        assertEquals("VALIDATION_FAILED", malformed.code());
        assertEquals("VALIDATION_FAILED", invalidContent.code());
        assertEquals("content", invalidContent.details().get(0).field());
    }

    @Test
    void rejectsClientSuppliedIdentityOrAuthorityFields() {
        BusinessException error = assertThrows(BusinessException.class, () -> validator.validate("""
                {"type":"CHAT_SUBMIT","requestId":"%s","payload":{"roomId":"%s","content":"hello","role":"SYSTEM_ADMIN"}}
                """.formatted(UUID.randomUUID(), UUID.randomUUID())));

        assertEquals("VALIDATION_FAILED", error.code());
        assertEquals("role", error.details().get(0).field());
        assertEquals("SERVER_ASSIGNED", error.details().get(0).reason());
    }
}
