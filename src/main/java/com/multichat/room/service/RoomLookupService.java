package com.multichat.room.service;

import com.multichat.infrastructure.mapper.ChatRoomMapper;
import com.multichat.infrastructure.redis.RoomInfoCache;
import com.multichat.room.entity.ChatRoom;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * The read-side entry point for active rooms. Room write services must call
 * {@link #invalidate(UUID)} only after their PostgreSQL transaction commits.
 */
@Service
public class RoomLookupService {
    private final ChatRoomMapper chatRoomMapper;
    private final RoomInfoCache roomInfoCache;

    public RoomLookupService(ChatRoomMapper chatRoomMapper, RoomInfoCache roomInfoCache) {
        this.chatRoomMapper = chatRoomMapper;
        this.roomInfoCache = roomInfoCache;
    }

    public Optional<ChatRoom> findActiveById(UUID roomId) {
        return roomInfoCache.getOrLoad(roomId, () -> chatRoomMapper.findActiveById(roomId));
    }

    public void invalidate(UUID roomId) {
        roomInfoCache.evict(roomId);
    }
}
