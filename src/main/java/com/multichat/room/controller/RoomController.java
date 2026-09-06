package com.multichat.room.controller;

import com.multichat.common.api.ApiResponse;
import com.multichat.room.dto.RoomPage;
import com.multichat.room.entity.ChatRoom;
import com.multichat.room.service.RoomService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {
    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<RoomPage<ChatRoom>>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String roomStatus,
            @RequestParam(required = false) String joinMode,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.success(roomService.list(name, roomStatus, joinMode, page, size)));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<ChatRoom>> get(@PathVariable UUID roomId) {
        return ResponseEntity.ok(ApiResponse.success(roomService.get(roomId)));
    }

}
