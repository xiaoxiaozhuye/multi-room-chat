package com.multichat.message.controller;

import com.multichat.common.api.ApiResponse;
import com.multichat.message.dto.AdminRoomMessageRequest;
import com.multichat.message.dto.BroadcastResult;
import com.multichat.message.dto.EmergencyNotificationResult;
import com.multichat.message.service.AdminRoomMessageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminRoomMessageController {
    private final AdminRoomMessageService adminRoomMessageService;

    public AdminRoomMessageController(AdminRoomMessageService adminRoomMessageService) {
        this.adminRoomMessageService = adminRoomMessageService;
    }

    @PostMapping("/broadcasts")
    public ResponseEntity<ApiResponse<BroadcastResult>> broadcast(@Valid @RequestBody AdminRoomMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.success(adminRoomMessageService.broadcast(request)));
    }

    @PostMapping("/emergency-notifications")
    public ResponseEntity<ApiResponse<EmergencyNotificationResult>> emergency(
            @Valid @RequestBody AdminRoomMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.success(adminRoomMessageService.publishEmergencyNotification(request)));
    }
}
