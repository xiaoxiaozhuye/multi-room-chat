package com.multichat.message.entity;

import java.time.Instant;
import java.util.UUID;

public record ChatMessage(UUID id, UUID requestId, UUID roomId, UUID senderId, Long roomSeq,
                          Long notificationSeq, String messageType, String content, String status,
                          Instant reviewDeadlineAt, Instant reviewedAt, Instant publishedAt, long version,
                          Instant createdAt) {
}
