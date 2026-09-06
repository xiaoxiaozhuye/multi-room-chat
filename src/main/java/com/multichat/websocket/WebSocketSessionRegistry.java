package com.multichat.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionRegistry {
    private final Map<String, WebSocketSession> sessionsByUser = new ConcurrentHashMap<>();

    public void register(String userId, WebSocketSession session) {
        sessionsByUser.put(userId, session);
    }

    /**
     * Returns true only if this closing session was still the user's active
     * session. This prevents an old connection from clearing new presence.
     */
    public boolean unregister(String userId, WebSocketSession session) {
        return sessionsByUser.remove(userId, session);
    }

    public Collection<WebSocketSession> sessions() {
        return sessionsByUser.values();
    }

    public int activeConnectionCount() {
        return sessionsByUser.size();
    }
}
