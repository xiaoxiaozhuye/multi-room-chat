package com.multichat.message.retention;

import java.time.Instant;
import java.util.UUID;

public record MessageRetentionRunResult(UUID runId, String status, Instant cutoffAt, int batchCount,
                                        int purgedCount, int retryCount, String lastError) {
    static MessageRetentionRunResult from(MessageRetentionRun run) {
        return new MessageRetentionRunResult(run.id(), run.status(), run.cutoffAt(), run.batchCount(),
                run.purgedCount(), run.retryCount(), run.lastError());
    }
}
