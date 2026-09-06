package com.multichat.message.controller;

import com.multichat.common.api.ApiResponse;
import com.multichat.message.dto.PersonalMessagePage;
import com.multichat.message.service.MessageHistoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Lists all ordinary messages submitted by the authenticated user. */
@Validated
@RestController
@RequestMapping("/api/v1/users/me/messages")
public class PersonalMessageController {
    private final MessageHistoryService historyService;

    public PersonalMessageController(MessageHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PersonalMessagePage>> list(
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) String messageStatus,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok(ApiResponse.success(historyService.personalMessages(roomId, messageStatus, cursor, limit)));
    }
}
