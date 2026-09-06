package com.multichat.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionRegistry {
    private final Map<String, WebSocketSession> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> subscriptionsBySessionId = new ConcurrentHashMap<>();

    public void register(String userId, WebSocketSession session) {
        sessionsByUser.put(userId, session);
        subscriptionsBySessionId.computeIfAbsent(session.getId(), ignored -> ConcurrentHashMap.newKeySet());
    }

    /**
     * Returns true only if this closing session was still the user's active
     * session. This prevents an old connection from clearing new presence.
     */
    public boolean unregister(String userId, WebSocketSession session) {
        subscriptionsBySessionId.remove(session.getId());
        return sessionsByUser.remove(userId, session);
    }

    public void subscribe(WebSocketSession session, UUID roomId) {
        subscriptionsBySessionId.computeIfAbsent(session.getId(), ignored -> ConcurrentHashMap.newKeySet()).add(roomId);
    }

    public void unsubscribe(WebSocketSession session, UUID roomId) {
        Set<UUID> rooms = subscriptionsBySessionId.get(session.getId());
        if (rooms != null) rooms.remove(roomId);
    }

    /** Removes every live session subscription for a user after its membership has committed as EXITED. */
    public void unsubscribeUserFromRoom(UUID userId, UUID roomId) {
        WebSocketSession session = sessionsByUser.get(userId.toString());
        if (session != null) unsubscribe(session, roomId);
    }

    public boolean isSubscribed(WebSocketSession session, UUID roomId) {
        return subscriptionsBySessionId.getOrDefault(session.getId(), Set.of()).contains(roomId);
    }

    public Collection<WebSocketSession> sessions() {
        return sessionsByUser.values();
    }

    public int activeConnectionCount() {
        return sessionsByUser.size();
    }
}
