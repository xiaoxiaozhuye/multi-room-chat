package com.multichat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multichat.message.entity.ChatMessage;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PersonalMessageNotifierTest {
    @Test
    void sendsTimeoutAsPersonalReviewStatus() throws Exception {
        WebSocketSessionRegistry sessions = mock(WebSocketSessionRegistry.class);
        WebSocketSession session = mock(WebSocketSession.class);
        ChatMessage message = new ChatMessage(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                4L, null, "CHAT", "hello", "TIMEOUT", Instant.parse("2026-09-06T10:00:30Z"),
                Instant.parse("2026-09-06T10:00:31Z"), null, 3, Instant.parse("2026-09-06T10:00:00Z"));
        when(sessions.sessionForUser(message.senderId())).thenReturn(Optional.of(session));
        when(session.isOpen()).thenReturn(true);

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        new PersonalMessageNotifier(sessions, objectMapper).notifyReviewResult(message, "TIMEOUT");

        var sent = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(sent.capture());
        var event = objectMapper.readTree(sent.getValue().getPayload());
        assertEquals("REVIEW_STATUS", event.path("type").asText());
        assertEquals(message.id().toString(), event.path("payload").path("messageId").asText());
        assertEquals("TIMEOUT", event.path("payload").path("messageStatus").asText());
        assertEquals(message.requestId().toString(), event.path("causationRequestId").asText());
        verify(session, never()).sendMessage(any(org.springframework.web.socket.BinaryMessage.class));
    }
}
