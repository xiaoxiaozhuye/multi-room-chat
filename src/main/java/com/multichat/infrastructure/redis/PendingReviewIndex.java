package com.multichat.infrastructure.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Disposable ZSet scheduler: member is a message UUID and score is its
 * persisted review deadline in epoch milliseconds.  It never contains bodies
 * or a review decision.
 */
@Component
public class PendingReviewIndex {
    private final StringRedisTemplate redis;
    private final RedisAccess redisAccess;

    public PendingReviewIndex(StringRedisTemplate redis, RedisAccess redisAccess) {
        this.redis = redis;
        this.redisAccess = redisAccess;
    }

    public boolean add(UUID messageId, Instant reviewDeadlineAt) {
        return redisAccess.write("review_pending_add", () -> redis.opsForZSet().add(
                RedisKeys.REVIEW_PENDING, messageId.toString(), reviewDeadlineAt.toEpochMilli()));
    }

    public boolean remove(UUID messageId) {
        return redisAccess.write("review_pending_remove", () -> redis.opsForZSet().remove(
                RedisKeys.REVIEW_PENDING, messageId.toString()));
    }

    public PendingReviewCandidates findDue(Instant now, int limit) {
        if (limit <= 0) {
            return new PendingReviewCandidates(List.of(), redisAccess.isEnabled());
        }
        RedisCallResult<Set<String>> result = redisAccess.read("review_pending_due", () ->
                redis.opsForZSet().rangeByScore(RedisKeys.REVIEW_PENDING,
                        Double.NEGATIVE_INFINITY, now.toEpochMilli(), 0, limit), Set.of());
        if (!result.available()) {
            return new PendingReviewCandidates(List.of(), false);
        }
        if (result.value() == null) {
            // Treat an unexpected null response as unavailable so timeout work
            // falls back to PostgreSQL instead of silently being skipped.
            return new PendingReviewCandidates(List.of(), false);
        }
        List<UUID> messageIds = new ArrayList<>();
        for (String value : result.value()) {
            try {
                messageIds.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                // A corrupt entry cannot affect PostgreSQL; delete it lazily.
                redisAccess.write("review_pending_remove_corrupt", () -> redis.opsForZSet().remove(
                        RedisKeys.REVIEW_PENDING, value));
            }
        }
        return new PendingReviewCandidates(messageIds, true);
    }

    public boolean isEnabled() {
        return redisAccess.isEnabled();
    }
}
