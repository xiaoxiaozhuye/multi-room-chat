package com.multichat.message.dto;

import java.time.Instant;
import java.util.UUID;

public record ReviewMessageQuery(UUID roomId, UUID senderId, String messageStatus,
                                 Instant createdFrom, Instant createdTo, int page, int size) {
}
