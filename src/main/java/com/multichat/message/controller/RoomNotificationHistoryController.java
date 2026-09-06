package com.multichat.message.controller;

import com.multichat.common.api.ApiResponse;
import com.multichat.message.dto.MessageCursorPage;
import com.multichat.message.service.MessageHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}/notifications")
public class RoomNotificationHistoryController {
    private final MessageHistoryService historyService;
    public RoomNotificationHistoryController(MessageHistoryService historyService) { this.historyService = historyService; }
    @GetMapping public ResponseEntity<ApiResponse<MessageCursorPage>> list(@PathVariable UUID roomId,
            @RequestParam(required = false) Long beforeSeq, @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.success(historyService.publishedNotificationHistory(roomId, beforeSeq, limit)));
    }
}
