package com.multichat.room.dto;

import java.util.List;

public record RoomPage<T>(List<T> items, int page, int size, boolean hasNext) {
    public RoomPage {
        items = List.copyOf(items);
    }
}
