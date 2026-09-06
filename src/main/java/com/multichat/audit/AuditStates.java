package com.multichat.audit;

import com.multichat.member.entity.RoomMembership;
import com.multichat.message.entity.ChatMessage;
import com.multichat.room.entity.ChatRoom;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stable, deliberately small state projections stored in audit history. */
public final class AuditStates {
    private AuditStates() { }

    public static Map<String, Object> membership(RoomMembership membership) {
        return state("id", membership.id(), "userId", membership.userId(), "roomId", membership.roomId(),
                "status", membership.status(), "requestedAt", membership.requestedAt(), "activatedAt", membership.activatedAt(),
                "version", membership.version());
    }

    public static Map<String, Object> room(ChatRoom room) {
        return state("id", room.id(), "name", room.name(), "description", room.description(), "maxMembers", room.maxMembers(),
                "joinMode", room.joinMode(), "status", room.status(), "createdBy", room.createdBy(), "version", room.version());
    }

    public static Map<String, Object> message(ChatMessage message) {
        return state("id", message.id(), "roomId", message.roomId(), "senderId", message.senderId(),
                "messageType", message.messageType(), "status", message.status(), "version", message.version(),
                "reviewDeadlineAt", message.reviewDeadlineAt(), "reviewedAt", message.reviewedAt(), "publishedAt", message.publishedAt());
    }

    public static Map<String, Object> detail(Object... pairs) { return state(pairs); }
    public static Map<String, Object> deletedRoom(ChatRoom room, Instant deletedAt) {
        return state("id", room.id(), "status", "DELETED", "deletedAt", deletedAt, "version", room.version() + 1);
    }

    private static Map<String, Object> state(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) if (pairs[i + 1] != null) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
}
