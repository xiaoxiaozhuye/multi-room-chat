package com.multichat.message.entity;

import java.time.Instant;
import java.util.UUID;

/** Minimal PostgreSQL projection used to rebuild the disposable Redis index. */
public record PendingReviewMessage(UUID id, Instant reviewDeadlineAt) {
}
