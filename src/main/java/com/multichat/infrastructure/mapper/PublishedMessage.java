package com.multichat.infrastructure.mapper;

import java.util.UUID;

/** A row emitted by PostgreSQL after it advances a room's publication cursor. */
public record PublishedMessage(UUID publishedMessageId, long publishedRoomSeq) {
}
