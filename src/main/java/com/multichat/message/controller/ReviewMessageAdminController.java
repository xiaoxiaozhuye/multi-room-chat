package com.multichat.message.controller;

import com.multichat.common.api.ApiResponse;
import com.multichat.message.dto.BatchReviewRequest;
import com.multichat.message.dto.BatchReviewResponse;
import com.multichat.message.dto.ReviewMessagePage;
import com.multichat.message.dto.ReviewMessageQuery;
import com.multichat.message.entity.ChatMessage;
import com.multichat.message.service.MessageReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/review-messages")
public class ReviewMessageAdminController {
    private final MessageReviewService reviewService;

    public ReviewMessageAdminController(MessageReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ReviewMessagePage>> query(
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) UUID senderId,
            @RequestParam(required = false, defaultValue = "PENDING_REVIEW") String messageStatus,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.query(
                new ReviewMessageQuery(roomId, senderId, messageStatus, createdFrom, createdTo, page, size))));
    }

    @PostMapping("/{messageId}/approve")
    public ResponseEntity<ApiResponse<ChatMessage>> approve(@PathVariable UUID messageId) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.approve(messageId)));
    }

    @PostMapping("/{messageId}/reject")
    public ResponseEntity<ApiResponse<ChatMessage>> reject(@PathVariable UUID messageId) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.reject(messageId)));
    }

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<BatchReviewResponse>> batch(@RequestBody BatchReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.batch(request)));
    }
}
