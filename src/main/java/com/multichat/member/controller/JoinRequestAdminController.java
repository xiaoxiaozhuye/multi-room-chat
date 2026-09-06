package com.multichat.member.controller;

import com.multichat.common.api.ApiResponse;
import com.multichat.member.entity.RoomMembership;
import com.multichat.member.service.MemberService;
import com.multichat.permission.PermissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/join-requests/{membershipId}")
public class JoinRequestAdminController {
    private final MemberService memberService;
    private final PermissionService permissionService;

    public JoinRequestAdminController(MemberService memberService, PermissionService permissionService) {
        this.memberService = memberService;
        this.permissionService = permissionService;
    }

    @PostMapping("/approve")
    public ResponseEntity<ApiResponse<RoomMembership>> approve(@PathVariable UUID membershipId) {
        return ResponseEntity.ok(ApiResponse.success(memberService.approve(membershipId, permissionService.currentActorId())));
    }

    @PostMapping("/reject")
    public ResponseEntity<ApiResponse<RoomMembership>> reject(@PathVariable UUID membershipId) {
        return ResponseEntity.ok(ApiResponse.success(memberService.reject(membershipId, permissionService.currentActorId())));
    }
}
