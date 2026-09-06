package com.multichat.infrastructure.redis;

/** A Redis result together with whether Redis was usable for this operation. */
public record RedisCallResult<T>(T value, boolean available) {
    public static <T> RedisCallResult<T> unavailable(T fallback) {
        return new RedisCallResult<>(fallback, false);
    }

    public static <T> RedisCallResult<T> available(T value) {
        return new RedisCallResult<>(value, true);
    }
}
