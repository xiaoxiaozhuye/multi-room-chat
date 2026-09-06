package com.multichat.infrastructure.redis;

import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.message.entity.PendingReviewMessage;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Re-populates Redis from PostgreSQL after a Redis flush/restart. Repeated
 * ZADD operations are idempotent and therefore safe while messages are being
 * submitted or reviewed.
 */
@Component
public class PendingReviewIndexRecovery {
    private final MessageMapper messageMapper;
    private final PendingReviewIndex pendingReviewIndex;

    public PendingReviewIndexRecovery(MessageMapper messageMapper, PendingReviewIndex pendingReviewIndex) {
        this.messageMapper = messageMapper;
        this.pendingReviewIndex = pendingReviewIndex;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildOnStartup() {
        rebuildFromPostgres();
    }

    @Scheduled(fixedDelayString = "${chat.redis.review-recovery-interval:PT30S}")
    public void rebuildPeriodically() {
        rebuildFromPostgres();
    }

    /** Public for operator-triggered recovery and integration tests. */
    public int rebuildFromPostgres() {
        if (!pendingReviewIndex.isEnabled()) {
            return 0;
        }
        int restored = 0;
        for (PendingReviewMessage message : messageMapper.findPendingReview()) {
            if (!pendingReviewIndex.add(message.id(), message.reviewDeadlineAt())) {
                // Redis is unavailable; continuing only creates duplicate logs.
                break;
            }
            restored++;
        }
        return restored;
    }
}
