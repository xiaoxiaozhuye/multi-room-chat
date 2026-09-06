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

/** Endpoints whose path belongs to the room resource rather than a membership collection. */
@RestController
@RequestMapping("/api/v1/rooms/{roomId}")
public class MemberLifecycleController {
    private final MemberService memberService;
    private final PermissionService permissionService;

    public MemberLifecycleController(MemberService memberService, PermissionService permissionService) {
        this.memberService = memberService;
        this.permissionService = permissionService;
    }

    @PostMapping("/leave")
    public ResponseEntity<ApiResponse<RoomMembership>> leave(@PathVariable UUID roomId) {
        return ResponseEntity.ok(ApiResponse.success(memberService.leave(roomId, permissionService.currentActorId())));
    }
}
