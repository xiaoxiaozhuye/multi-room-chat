package com.multichat.message.dto;

import java.util.List;
import java.util.UUID;

public record BroadcastResult(UUID broadcastId, List<AdminRoomMessageItemResult> results) {
}
