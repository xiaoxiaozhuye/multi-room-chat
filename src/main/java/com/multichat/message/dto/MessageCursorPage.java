package com.multichat.message.dto;

import com.multichat.message.entity.ChatMessage;

import java.util.List;

/** A room-sequence cursor page.  Pass {@code nextBeforeSeq} to retrieve older rows. */
public record MessageCursorPage(List<ChatMessage> items, Long nextBeforeSeq, boolean hasMore) {
    public MessageCursorPage {
        items = List.copyOf(items);
    }
}
