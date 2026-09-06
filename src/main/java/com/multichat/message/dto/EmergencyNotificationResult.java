package com.multichat.message.dto;

import java.util.List;
import java.util.UUID;

public record EmergencyNotificationResult(UUID notificationId, List<AdminRoomMessageItemResult> results) {
}
