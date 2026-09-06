package com.multichat.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionRegistry {
    private final Map<String, WebSocketSession> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, String> userBySessionId = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> subscriptionsBySessionId = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> usersByRoom = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastPongBySessionId = new ConcurrentHashMap<>();

    /** Atomically makes this the user's sole node-local connection. */
    public synchronized Optional<WebSocketSession> register(String userId, WebSocketSession session) {
        WebSocketSession previous = sessionsByUser.put(userId, session);
        userBySessionId.put(session.getId(), userId);
        subscriptionsBySessionId.computeIfAbsent(session.getId(), ignored -> ConcurrentHashMap.newKeySet());
        lastPongBySessionId.put(session.getId(), Instant.now());
        if (previous != null && previous != session) {
            removeSubscriptions(previous.getId(), userId);
            userBySessionId.remove(previous.getId(), userId);
            lastPongBySessionId.remove(previous.getId());
        }
        return previous == session ? Optional.empty() : Optional.ofNullable(previous);
    }

    /**
     * Returns true only if this closing session was still the user's active
     * session. This prevents an old connection from clearing new presence.
     */
    public synchronized boolean unregister(String userId, WebSocketSession session) {
        removeSubscriptions(session.getId(), userId);
        userBySessionId.remove(session.getId(), userId);
        lastPongBySessionId.remove(session.getId());
        return sessionsByUser.remove(userId, session);
    }

    public synchronized void subscribe(WebSocketSession session, UUID roomId) {
        String userId = userBySessionId.get(session.getId());
        if (userId == null || sessionsByUser.get(userId) != session) return;
        subscriptionsBySessionId.computeIfAbsent(session.getId(), ignored -> ConcurrentHashMap.newKeySet()).add(roomId);
        usersByRoom.computeIfAbsent(roomId, ignored -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    public synchronized void unsubscribe(WebSocketSession session, UUID roomId) {
        String userId = userBySessionId.get(session.getId());
        Set<UUID> rooms = subscriptionsBySessionId.get(session.getId());
        if (rooms != null) rooms.remove(roomId);
        if (userId != null) removeRoomUser(roomId, userId);
    }

    /** Removes every live session subscription for a user after its membership has committed as EXITED. */
    public synchronized Optional<WebSocketSession> unsubscribeUserFromRoom(UUID userId, UUID roomId) {
        WebSocketSession session = sessionsByUser.get(userId.toString());
        if (session != null) unsubscribe(session, roomId);
        return Optional.ofNullable(session);
    }

    /** Removes every current subscription for a deleted room. */
    public synchronized Collection<WebSocketSession> unsubscribeRoom(UUID roomId) {
        Set<String> users = usersByRoom.remove(roomId);
        if (users == null || users.isEmpty()) return List.of();
        return users.stream().map(sessionsByUser::get).filter(java.util.Objects::nonNull)
                .peek(session -> {
                    Set<UUID> rooms = subscriptionsBySessionId.get(session.getId());
                    if (rooms != null) rooms.remove(roomId);
                }).toList();
    }

    public boolean isSubscribed(WebSocketSession session, UUID roomId) {
        return subscriptionsBySessionId.getOrDefault(session.getId(), Set.of()).contains(roomId);
    }

    public Collection<WebSocketSession> sessions() {
        return List.copyOf(sessionsByUser.values());
    }

    /** Snapshotted routing targets for one room, derived from roomId -> userId. */
    public Collection<WebSocketSession> sessionsForRoom(UUID roomId) {
        Set<String> users = usersByRoom.get(roomId);
        if (users == null) return List.of();
        return users.stream().map(sessionsByUser::get)
                .filter(session -> session != null && isSubscribed(session, roomId)).toList();
    }

    public Optional<String> userIdFor(WebSocketSession session) {
        return Optional.ofNullable(userBySessionId.get(session.getId()));
    }

    /** The personal channel is the user's current authenticated connection. */
    public Optional<WebSocketSession> sessionForUser(UUID userId) {
        return Optional.ofNullable(sessionsByUser.get(userId.toString()));
    }

    public void recordPong(WebSocketSession session) {
        if (userBySessionId.containsKey(session.getId())) lastPongBySessionId.put(session.getId(), Instant.now());
    }

    public Instant lastPongAt(WebSocketSession session) {
        return lastPongBySessionId.get(session.getId());
    }

    public int activeConnectionCount() {
        return sessionsByUser.size();
    }

    private void removeSubscriptions(String sessionId, String userId) {
        Set<UUID> rooms = subscriptionsBySessionId.remove(sessionId);
        if (rooms != null) rooms.forEach(roomId -> removeRoomUser(roomId, userId));
    }

    private void removeRoomUser(UUID roomId, String userId) {
        Set<String> users = usersByRoom.get(roomId);
        if (users == null) return;
        users.remove(userId);
        if (users.isEmpty()) usersByRoom.remove(roomId, users);
    }
}
