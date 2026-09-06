package com.multichat.audit.controller;

import com.multichat.audit.service.AuditService;
import com.multichat.audit.service.dto.AuditPage;
import com.multichat.audit.service.dto.AuditQuery;
import com.multichat.common.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/admin/audits", "/api/v1/admin/audit-logs"})
public class AuditController {
    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** The service enforces SYSTEM_ADMIN; URL shape alone never grants audit access. */
    @GetMapping
    public ResponseEntity<ApiResponse<AuditPage>> query(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) UUID messageId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(auditService.query(
                new AuditQuery(actorId, roomId, messageId, action, from, to, page, size))));
    }
}
