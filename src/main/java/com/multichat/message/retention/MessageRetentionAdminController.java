package com.multichat.message.retention;

import com.multichat.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/retention/messages")
public class MessageRetentionAdminController {
    private final MessageRetentionService retentionService;

    public MessageRetentionAdminController(MessageRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @PostMapping("/purge")
    public ResponseEntity<ApiResponse<MessageRetentionRunResult>> purge(@Valid @RequestBody ManualMessageRetentionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(retentionService.runManually(request.confirmation())));
    }
}
