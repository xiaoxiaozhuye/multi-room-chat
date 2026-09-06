package com.multichat.infrastructure.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Bounds for data that is deliberately ephemeral in Redis.  None of these
 * values change the authoritative PostgreSQL state.
 */
@ConfigurationProperties(prefix = "chat.redis")
public class RedisInfrastructureProperties {
    private boolean enabled = true;
    private Duration roomInfoTtl = Duration.ofMinutes(5);
    private Duration onlineUserTtl = Duration.ofSeconds(70);
    private Duration rateWindow = Duration.ofMinutes(1);
    private int messagesPerSecond = 2;
    private int messagesPerMinute = 20;
    private Duration reviewRecoveryInterval = Duration.ofSeconds(30);
    private int reviewScanBatchSize = 100;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Duration getRoomInfoTtl() { return roomInfoTtl; }
    public void setRoomInfoTtl(Duration roomInfoTtl) { this.roomInfoTtl = roomInfoTtl; }

    public Duration getOnlineUserTtl() { return onlineUserTtl; }
    public void setOnlineUserTtl(Duration onlineUserTtl) { this.onlineUserTtl = onlineUserTtl; }

    public Duration getRateWindow() { return rateWindow; }
    public void setRateWindow(Duration rateWindow) { this.rateWindow = rateWindow; }

    public int getMessagesPerSecond() { return messagesPerSecond; }
    public void setMessagesPerSecond(int messagesPerSecond) { this.messagesPerSecond = messagesPerSecond; }

    public int getMessagesPerMinute() { return messagesPerMinute; }
    public void setMessagesPerMinute(int messagesPerMinute) { this.messagesPerMinute = messagesPerMinute; }

    public Duration getReviewRecoveryInterval() { return reviewRecoveryInterval; }
    public void setReviewRecoveryInterval(Duration reviewRecoveryInterval) {
        this.reviewRecoveryInterval = reviewRecoveryInterval;
    }

    public int getReviewScanBatchSize() { return reviewScanBatchSize; }
    public void setReviewScanBatchSize(int reviewScanBatchSize) { this.reviewScanBatchSize = reviewScanBatchSize; }
}
