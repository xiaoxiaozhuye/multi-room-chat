package com.multichat.infrastructure.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Atomic sliding-window limiter.  One ZSet per user retains only the most
 * recent minute, enforcing both 2 messages/second and 20 messages/minute.
 */
@Component
public class UserMessageRateLimiter {
    private static final DefaultRedisScript<List> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local perSecond = tonumber(ARGV[2])
            local perMinute = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])
            local member = ARGV[5]
            local secondStart = now - 1000
            local minuteStart = now - 60000
            redis.call('ZREMRANGEBYSCORE', key, '-inf', minuteStart)
            local secondCount = redis.call('ZCOUNT', key, secondStart, '+inf')
            if secondCount >= perSecond then
                local oldest = redis.call('ZRANGEBYSCORE', key, secondStart, '+inf', 'WITHSCORES', 'LIMIT', 0, 1)
                return {0, math.max(1, tonumber(oldest[2]) + 1000 - now)}
            end
            local minuteCount = redis.call('ZCARD', key)
            if minuteCount >= perMinute then
                local oldest = redis.call('ZRANGEBYSCORE', key, 0, '+inf', 'WITHSCORES', 'LIMIT', 0, 1)
                return {0, math.max(1, tonumber(oldest[2]) + 60000 - now)}
            end
            redis.call('ZADD', key, now, member)
            redis.call('PEXPIRE', key, ttl)
            return {1, 0}
            """, List.class);

    private final StringRedisTemplate redis;
    private final RedisInfrastructureProperties properties;
    private final RedisAccess redisAccess;
    private final Clock clock;

    @Autowired
    public UserMessageRateLimiter(StringRedisTemplate redis, RedisInfrastructureProperties properties,
                                  RedisAccess redisAccess) {
        this(redis, properties, redisAccess, Clock.systemUTC());
    }

    UserMessageRateLimiter(StringRedisTemplate redis, RedisInfrastructureProperties properties,
                           RedisAccess redisAccess, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.redisAccess = redisAccess;
        this.clock = clock;
    }

    public RateLimitDecision tryAcquire(UUID userId) {
        long now = clock.millis();
        long ttlMillis = properties.getRateWindow().toMillis();
        RedisCallResult<List> result = redisAccess.read("user_rate_acquire", () -> redis.execute(
                ACQUIRE_SCRIPT, List.of(RedisKeys.userRate(userId)), Long.toString(now),
                Integer.toString(properties.getMessagesPerSecond()),
                Integer.toString(properties.getMessagesPerMinute()), Long.toString(ttlMillis),
                UUID.randomUUID().toString()), null);
        if (!result.available() || result.value() == null || result.value().isEmpty()) {
            return RateLimitDecision.degradedAllowed();
        }

        boolean allowed = asLong(result.value().get(0)) == 1L;
        if (allowed) {
            return RateLimitDecision.permitted();
        }
        long retryMillis = result.value().size() > 1 ? asLong(result.value().get(1)) : 1L;
        return RateLimitDecision.rejected(Duration.ofMillis(Math.max(1L, retryMillis)));
    }

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }
}
