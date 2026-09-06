package com.multichat.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketSessionRegistryTest {
    @Test
    void oneSessionCanSubscribeToManyRoomsAndReplacementCannotClearTheNewSession() {
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
        WebSocketSession oldSession = session("old");
        WebSocketSession newSession = session("new");
        String userId = UUID.randomUUID().toString();
        UUID firstRoom = UUID.randomUUID();
        UUID secondRoom = UUID.randomUUID();

        assertTrue(registry.register(userId, oldSession).isEmpty());
        registry.subscribe(oldSession, firstRoom);
        registry.subscribe(oldSession, secondRoom);
        assertEquals(1, registry.sessionsForRoom(firstRoom).size());
        assertEquals(1, registry.sessionsForRoom(secondRoom).size());

        assertSame(oldSession, registry.register(userId, newSession).orElseThrow());
        assertTrue(registry.sessionsForRoom(firstRoom).isEmpty());
        assertFalse(registry.unregister(userId, oldSession));
        assertSame(newSession, registry.sessionForUser(UUID.fromString(userId)).orElseThrow());
        assertTrue(registry.unregister(userId, newSession));
        assertEquals(0, registry.activeConnectionCount());
    }

    @Test
    void unsubscribeRemovesTheReverseRoomRouteImmediately() {
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
        WebSocketSession session = session("only");
        String userId = UUID.randomUUID().toString();
        UUID roomId = UUID.randomUUID();

        registry.register(userId, session);
        registry.subscribe(session, roomId);
        registry.unsubscribe(session, roomId);

        assertTrue(registry.sessionsForRoom(roomId).isEmpty());
        assertFalse(registry.isSubscribed(session, roomId));
    }

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        return session;
    }
}
