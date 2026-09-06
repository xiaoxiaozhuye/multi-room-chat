package com.multichat.message.dto;

import java.time.Instant;
import java.util.UUID;

/** Minimal personal-only view of the review lifecycle for one normal message. */
public record PersonalMessageReviewStatus(UUID messageId, UUID roomId, Long roomSeq, String messageStatus,
                                          Instant reviewDeadlineAt, Instant reviewedAt, Instant publishedAt) {
}
