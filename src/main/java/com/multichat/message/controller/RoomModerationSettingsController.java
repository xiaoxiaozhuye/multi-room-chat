package com.multichat.message.controller;

import com.multichat.common.api.ApiResponse;
import com.multichat.message.service.ModerationSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/rooms/{roomId}/moderation")
public class RoomModerationSettingsController {
    private final ModerationSettingsService settingsService;

    public RoomModerationSettingsController(ModerationSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<RoomModerationSettings>> get(@PathVariable UUID roomId) {
        settingsService.requireRoomPermission(roomId);
        return ResponseEntity.ok(ApiResponse.success(new RoomModerationSettings(settingsService.isEnabled(roomId))));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<RoomModerationSettings>> update(
            @PathVariable UUID roomId, @Valid @RequestBody UpdateRoomModerationSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(new RoomModerationSettings(
                settingsService.updateRoom(roomId, request.enabled()))));
    }

    public record RoomModerationSettings(boolean enabled) { }

    public record UpdateRoomModerationSettingsRequest(@NotNull Boolean enabled) { }
}
