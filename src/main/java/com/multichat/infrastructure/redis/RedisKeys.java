package com.multichat.infrastructure.redis;

import java.util.UUID;

public final class RedisKeys {
    public static final String REVIEW_PENDING = "review:pending";

    private RedisKeys() { }

    public static String onlineUser(UUID userId) { return "online:user:" + userId; }
    public static String roomInfo(UUID roomId) { return "room:info:" + roomId; }
    public static String userRate(UUID userId) { return "rate:user:" + userId; }
}
