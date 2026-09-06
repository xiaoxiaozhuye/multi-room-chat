package com.multichat.infrastructure.redis;

import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.message.entity.PendingReviewMessage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Chooses Redis for the normal timeout path and PostgreSQL when Redis is
 * unavailable. Consumers must still conditionally transition the returned
 * messages in PostgreSQL; candidates alone never determine final state.
 */
@Component
public class PendingReviewDueScanner {
    private final PendingReviewIndex pendingReviewIndex;
    private final MessageMapper messageMapper;

    public PendingReviewDueScanner(PendingReviewIndex pendingReviewIndex, MessageMapper messageMapper) {
        this.pendingReviewIndex = pendingReviewIndex;
        this.messageMapper = messageMapper;
    }

    public List<PendingReviewMessage> findDue(Instant now, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        PendingReviewCandidates candidates = pendingReviewIndex.findDue(now, limit);
        if (!candidates.redisAvailable()) {
            return messageMapper.findPendingReviewDue(now, limit);
        }
        // Refetching verifies the candidate is still PENDING_REVIEW in PostgreSQL.
        return candidates.messageIds().stream()
                .map(messageMapper::findById)
                .flatMap(java.util.Optional::stream)
                .filter(message -> "PENDING_REVIEW".equals(message.status()))
                .filter(message -> message.reviewDeadlineAt() != null && !message.reviewDeadlineAt().isAfter(now))
                .map(message -> new PendingReviewMessage(message.id(), message.reviewDeadlineAt()))
                .toList();
    }
}
