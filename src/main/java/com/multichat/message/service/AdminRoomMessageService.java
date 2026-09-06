package com.multichat.message.service;

import com.multichat.message.dto.AdminRoomMessageRequest;
import com.multichat.message.dto.BroadcastResult;
import com.multichat.message.dto.EmergencyNotificationResult;

public interface AdminRoomMessageService {
    BroadcastResult broadcast(AdminRoomMessageRequest request);

    EmergencyNotificationResult publishEmergencyNotification(AdminRoomMessageRequest request);
}
