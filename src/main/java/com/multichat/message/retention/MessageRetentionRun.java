package com.multichat.message.retention;

import java.time.Instant;
import java.util.UUID;

public record MessageRetentionRun(UUID id, String triggerType, UUID requestedBy, Instant cutoffAt, String status,
                                  int batchCount, int purgedCount, int retryCount, String lastError,
                                  Instant startedAt, Instant completedAt) {
}
