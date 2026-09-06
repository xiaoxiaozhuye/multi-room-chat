package com.multichat.room;

import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import com.multichat.infrastructure.mapper.ChatRoomMapper;
import com.multichat.room.entity.ChatRoom;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Authoritative status/deletion guard shared by join, subscription and message
 * command paths.  A deleted row is deliberately distinguished from an unknown
 * room so callers never accidentally treat a historical room as usable.
 */
@Component
public class RoomStatePolicy {
    private final ChatRoomMapper roomMapper;

    public RoomStatePolicy(ChatRoomMapper roomMapper) {
        this.roomMapper = roomMapper;
    }

    public ChatRoom requireExisting(UUID roomId) {
        return roomMapper.findActiveById(roomId).orElseGet(() -> {
            if (roomMapper.isDeleted(roomId)) throw new BusinessException(ErrorCode.ROOM_DELETED);
            throw new BusinessException(ErrorCode.ROOM_NOT_FOUND);
        });
    }

    public ChatRoom requireJoinable(UUID roomId) {
        ChatRoom room = requireExisting(roomId);
        requireActive(room);
        return room;
    }

    /** PAUSED and CLOSED retain read/subscription access for existing members. */
    public ChatRoom requireSubscribable(UUID roomId) {
        return requireExisting(roomId);
    }

    public ChatRoom requireUserChatAllowed(UUID roomId) {
        ChatRoom room = requireExisting(roomId);
        requireActive(room);
        return room;
    }

    /** Operators may publish administrative messages in every non-deleted state. */
    public ChatRoom requireAdminMessageAllowed(UUID roomId) {
        return requireExisting(roomId);
    }

    private void requireActive(ChatRoom room) {
        if ("PAUSED".equals(room.status())) throw new BusinessException(ErrorCode.ROOM_PAUSED);
        if ("CLOSED".equals(room.status())) throw new BusinessException(ErrorCode.ROOM_CLOSED);
        if (!"ACTIVE".equals(room.status())) throw new BusinessException(ErrorCode.ROOM_STATE_CONFLICT);
    }
}
