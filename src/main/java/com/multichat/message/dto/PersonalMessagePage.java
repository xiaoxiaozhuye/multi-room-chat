package com.multichat.message.dto;

import java.util.List;

/** Keyset page for /users/me/messages, ordered newest first. */
public record PersonalMessagePage(List<PersonalMessageItem> items, String nextCursor, boolean hasMore) {
    public PersonalMessagePage {
        items = List.copyOf(items);
    }
}
