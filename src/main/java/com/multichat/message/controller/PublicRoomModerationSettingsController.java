package com.multichat.message.controller;

import com.multichat.common.api.ApiResponse;
import com.multichat.message.service.ModerationSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}/moderation")
public class PublicRoomModerationSettingsController {
    private final ModerationSettingsService settingsService;

    public PublicRoomModerationSettingsController(ModerationSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<RoomModerationSettings>> get(@PathVariable UUID roomId) {
        return ResponseEntity.ok(ApiResponse.success(new RoomModerationSettings(
                settingsService.isEnabledForMember(roomId))));
    }

    public record RoomModerationSettings(boolean enabled) { }
}
