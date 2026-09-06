package com.multichat.message.dto;

import java.time.Instant;
import java.util.UUID;

/** Client-facing projection for the authenticated user's message list. */
public record PersonalMessageItem(UUID messageId, UUID roomId, Long roomSeq, Long notificationSeq,
                                  UUID senderId, String messageType, String content, String messageStatus,
                                  Instant createdAt, Instant reviewDeadlineAt, Instant reviewedAt,
                                  Instant publishedAt, String roomName, boolean roomDeleted) {
}
