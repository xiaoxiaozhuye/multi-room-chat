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
@RequestMapping("/api/v1/rooms/{roomId}/memberships")
public class MemberController {
    private final MemberService memberService;
    private final PermissionService permissionService;

    public MemberController(MemberService memberService, PermissionService permissionService) {
        this.memberService = memberService;
        this.permissionService = permissionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoomMembership>> join(@PathVariable UUID roomId) {
        return ResponseEntity.ok(ApiResponse.success(memberService.join(roomId, permissionService.currentActorId())));
    }
}
