package com.multichat.message.controller;

import com.multichat.common.api.ApiResponse;
import com.multichat.message.dto.MessageCursorPage;
import com.multichat.message.dto.PersonalMessageReviewStatus;
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

@Validated
@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {
    private final MessageHistoryService historyService;

    public MessageController(MessageHistoryService historyService) {
        this.historyService = historyService;
    }

    /** Includes this caller's PENDING_REVIEW, REJECTED and TIMEOUT messages. */
    @GetMapping("/me/rooms/{roomId}")
    public ResponseEntity<ApiResponse<MessageCursorPage>> mine(
            @PathVariable UUID roomId,
            @RequestParam(required = false) Long beforeSeq,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok(ApiResponse.success(historyService.personalRoomHistory(roomId, beforeSeq, limit)));
    }

    @GetMapping("/{messageId}/review-status")
    public ResponseEntity<ApiResponse<PersonalMessageReviewStatus>> reviewStatus(@PathVariable UUID messageId) {
        return ResponseEntity.ok(ApiResponse.success(historyService.personalReviewStatus(messageId)));
    }
}
