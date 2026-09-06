package com.multichat.message.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/** Externalized moderation policy; words are never read from Redis or the database. */
@ConfigurationProperties(prefix = "chat.moderation")
public record ModerationProperties(List<String> sensitiveWords, String matchMode, Duration reviewTimeout) {
    public ModerationProperties {
        sensitiveWords = sensitiveWords == null ? List.of() : List.copyOf(sensitiveWords);
        matchMode = matchMode == null || matchMode.isBlank() ? "CONTAINS" : matchMode;
        reviewTimeout = reviewTimeout == null ? Duration.ofSeconds(30) : reviewTimeout;
        if (reviewTimeout.isNegative() || reviewTimeout.isZero()) {
            throw new IllegalArgumentException("chat.moderation.review-timeout must be positive");
        }
    }
}
