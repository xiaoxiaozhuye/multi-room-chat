package com.multichat.message.controller;

import com.multichat.common.api.ApiResponse;
import com.multichat.message.dto.MessageCursorPage;
import com.multichat.message.service.MessageHistoryService;
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

/** Public room history has one visibility rule: only published messages. */
@Validated
@RestController
@RequestMapping("/api/v1/rooms/{roomId}/messages")
public class RoomMessageHistoryController {
    private final MessageHistoryService historyService;

    public RoomMessageHistoryController(MessageHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MessageCursorPage>> published(
            @PathVariable UUID roomId,
            @RequestParam(required = false) Long beforeSeq,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok(ApiResponse.success(historyService.publishedRoomHistory(roomId, beforeSeq, limit)));
    }
}
