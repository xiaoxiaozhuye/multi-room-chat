package com.multichat.message.service;

import com.multichat.infrastructure.redis.PendingReviewDueScanner;
import com.multichat.infrastructure.redis.RedisInfrastructureProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Periodically resolves due review candidates; PostgreSQL remains authoritative. */
@Component
public class PendingReviewTimeoutScheduler {
    private final PendingReviewDueScanner dueScanner;
    private final MessageTimeoutTransactionProcessor timeoutProcessor;
    private final RedisInfrastructureProperties redisProperties;

    public PendingReviewTimeoutScheduler(PendingReviewDueScanner dueScanner,
                                         MessageTimeoutTransactionProcessor timeoutProcessor,
                                         RedisInfrastructureProperties redisProperties) {
        this.dueScanner = dueScanner;
        this.timeoutProcessor = timeoutProcessor;
        this.redisProperties = redisProperties;
    }

    @Scheduled(fixedDelayString = "${chat.review.scan-interval:PT1S}")
    public void scanDueMessages() {
        Instant now = Instant.now();
        int limit = Math.max(1, redisProperties.getReviewScanBatchSize());
        dueScanner.findDue(now, limit)
                .forEach(message -> timeoutProcessor.timeout(message.id(), now));
    }
}
