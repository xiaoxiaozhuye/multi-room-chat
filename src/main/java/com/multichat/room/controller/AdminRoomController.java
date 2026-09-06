package com.multichat.room.controller;

import com.multichat.common.api.ApiResponse;
import com.multichat.room.dto.CreateRoomRequest;
import com.multichat.room.dto.RoomDeletionResponse;
import com.multichat.room.dto.UpdateRoomRequest;
import com.multichat.room.entity.ChatRoom;
import com.multichat.room.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/rooms")
public class AdminRoomController {
    private final RoomService roomService;

    public AdminRoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ChatRoom>> create(@Valid @RequestBody CreateRoomRequest request) {
        return ResponseEntity.ok(ApiResponse.success(roomService.create(null, request)));
    }

    @PatchMapping("/{roomId}")
    public ResponseEntity<ApiResponse<ChatRoom>> update(@PathVariable UUID roomId,
                                                          @Valid @RequestBody UpdateRoomRequest request) {
        return ResponseEntity.ok(ApiResponse.success(roomService.update(roomId, request)));
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<ApiResponse<RoomDeletionResponse>> delete(@PathVariable UUID roomId) {
        return ResponseEntity.ok(ApiResponse.success(roomService.delete(roomId)));
    }
}
