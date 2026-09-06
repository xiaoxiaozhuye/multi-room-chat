package com.multichat.permission.controller;

import com.multichat.common.api.ApiResponse;
import com.multichat.permission.dto.RoomAuthorizationGrantResponse;
import com.multichat.permission.dto.RoomAuthorizationResponse;
import com.multichat.permission.dto.RoomAuthorizationRevokeResponse;
import com.multichat.permission.service.RoomAuthorizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The authenticated JWT principal, rather than request input, identifies the operator. */
@RestController
@RequestMapping("/api/v1/admin/rooms")
public class RoomAuthorizationController {
    private final RoomAuthorizationService roomAuthorizationService;

    public RoomAuthorizationController(RoomAuthorizationService roomAuthorizationService) {
        this.roomAuthorizationService = roomAuthorizationService;
    }

    @GetMapping("/{roomId}/authorizations")
    public ResponseEntity<ApiResponse<List<RoomAuthorizationResponse>>> list(@PathVariable UUID roomId) {
        return ResponseEntity.ok(ApiResponse.success(roomAuthorizationService.list(roomId)));
    }

    @PutMapping("/{roomId}/authorizations/{adminUserId}")
    public ResponseEntity<ApiResponse<RoomAuthorizationGrantResponse>> grant(@PathVariable UUID roomId,
                                                                               @PathVariable UUID adminUserId) {
        return ResponseEntity.ok(ApiResponse.success(roomAuthorizationService.grant(roomId, adminUserId)));
    }

    @DeleteMapping("/{roomId}/authorizations/{adminUserId}")
    public ResponseEntity<ApiResponse<RoomAuthorizationRevokeResponse>> revoke(@PathVariable UUID roomId,
                                                                                  @PathVariable UUID adminUserId) {
        return ResponseEntity.ok(ApiResponse.success(roomAuthorizationService.revoke(roomId, adminUserId)));
    }
}
