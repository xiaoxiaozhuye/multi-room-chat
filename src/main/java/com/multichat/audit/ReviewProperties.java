package com.multichat.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "chat.review")
public record ReviewProperties(Integer timeoutSeconds, Duration scanInterval) {
    public ReviewProperties {
        timeoutSeconds = timeoutSeconds == null ? 30 : timeoutSeconds;
        scanInterval = scanInterval == null ? Duration.ofSeconds(1) : scanInterval;
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("chat.review.timeout-seconds must be positive");
        }
        if (scanInterval.isNegative() || scanInterval.isZero()) {
            throw new IllegalArgumentException("chat.review.scan-interval must be positive");
        }
    }

    public Duration timeout() {
        return Duration.ofSeconds(timeoutSeconds);
    }
}
