package com.multichat.infrastructure.redis;

import java.time.Duration;

/** A degraded decision is allowed because Redis is not the source of truth. */
public record RateLimitDecision(boolean allowed, boolean enforced, Duration retryAfter) {
    public static RateLimitDecision permitted() {
        return new RateLimitDecision(true, true, Duration.ZERO);
    }

    public static RateLimitDecision rejected(Duration retryAfter) {
        return new RateLimitDecision(false, true, retryAfter);
    }

    public static RateLimitDecision degradedAllowed() {
        return new RateLimitDecision(true, false, Duration.ZERO);
    }
}
