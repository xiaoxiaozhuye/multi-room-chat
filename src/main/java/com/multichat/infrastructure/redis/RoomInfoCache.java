package com.multichat.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multichat.room.entity.ChatRoom;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cache-aside access for active room metadata.  The loader is normally backed
 * by {@code ChatRoomMapper}; a failed Redis lookup simply invokes that loader.
 */
@Component
public class RoomInfoCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RedisInfrastructureProperties properties;
    private final RedisAccess redisAccess;

    public RoomInfoCache(StringRedisTemplate redis, ObjectMapper objectMapper,
                         RedisInfrastructureProperties properties, RedisAccess redisAccess) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.redisAccess = redisAccess;
    }

    public Optional<ChatRoom> getOrLoad(UUID roomId, Supplier<Optional<ChatRoom>> postgresLoader) {
        String key = RedisKeys.roomInfo(roomId);
        RedisCallResult<String> cached = redisAccess.read("room_info_get",
                () -> redis.opsForValue().get(key), null);
        if (cached.available() && cached.value() != null) {
            try {
                return Optional.of(objectMapper.readValue(cached.value(), ChatRoom.class));
            } catch (JsonProcessingException exception) {
                // A malformed old cache entry is disposable; avoid serving it.
                evict(roomId);
            }
        }

        Optional<ChatRoom> room = postgresLoader.get();
        room.ifPresent(value -> put(value));
        return room;
    }

    public void put(ChatRoom room) {
        try {
            String payload = objectMapper.writeValueAsString(room);
            redisAccess.write("room_info_put", () -> redis.opsForValue().set(
                    RedisKeys.roomInfo(room.id()), payload, properties.getRoomInfoTtl()));
        } catch (JsonProcessingException exception) {
            // Serialization failure is a cache miss, never a room-query failure.
        }
    }

    /** Call after the PostgreSQL room mutation commits. */
    public void evict(UUID roomId) {
        redisAccess.write("room_info_evict", () -> redis.delete(RedisKeys.roomInfo(roomId)));
    }
}
