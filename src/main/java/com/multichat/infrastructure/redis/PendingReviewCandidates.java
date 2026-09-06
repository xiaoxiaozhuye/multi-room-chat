package com.multichat.infrastructure.redis;

import java.util.List;
import java.util.UUID;

/** Redis candidates and the source used to produce them. */
public record PendingReviewCandidates(List<UUID> messageIds, boolean redisAvailable) {
    public PendingReviewCandidates {
        messageIds = List.copyOf(messageIds);
    }
}
