package com.multichat.infrastructure.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Ephemeral presence with a heartbeat-renewed TTL; it is not an authorization input. */
@Component
public class OnlineStatusService {
    private static final String ONLINE_VALUE = "1";

    private final StringRedisTemplate redis;
    private final RedisInfrastructureProperties properties;
    private final RedisAccess redisAccess;

    public OnlineStatusService(StringRedisTemplate redis, RedisInfrastructureProperties properties,
                               RedisAccess redisAccess) {
        this.redis = redis;
        this.properties = properties;
        this.redisAccess = redisAccess;
    }

    public void markOnline(UUID userId) {
        redisAccess.write("online_user_set", () -> redis.opsForValue().set(
                RedisKeys.onlineUser(userId), ONLINE_VALUE, properties.getOnlineUserTtl()));
    }

    /** Refresh on each received application message or WebSocket pong. */
    public void refresh(UUID userId) {
        markOnline(userId);
    }

    public void markOffline(UUID userId) {
        redisAccess.write("online_user_delete", () -> redis.delete(RedisKeys.onlineUser(userId)));
    }

    public OnlineStatus statusOf(UUID userId) {
        RedisCallResult<Boolean> result = redisAccess.read("online_user_get",
                () -> Boolean.TRUE.equals(redis.hasKey(RedisKeys.onlineUser(userId))), false);
        if (!result.available()) {
            return OnlineStatus.UNKNOWN;
        }
        return result.value() ? OnlineStatus.ONLINE : OnlineStatus.OFFLINE;
    }
}
