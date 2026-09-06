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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.multichat.infrastructure.mapper.RoomMembershipMapper;
import com.multichat.room.dto.RoomPage;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/join-requests")
public class JoinRequestAdminController {
    private final MemberService memberService;
    private final PermissionService permissionService;
    private final RoomMembershipMapper membershipMapper;

    public JoinRequestAdminController(MemberService memberService, PermissionService permissionService, RoomMembershipMapper membershipMapper) {
        this.memberService = memberService;
        this.permissionService = permissionService;
        this.membershipMapper = membershipMapper;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<RoomPage<RoomMembership>>> list(@RequestParam(required=false) UUID roomId,@RequestParam(required=false) UUID userId,@RequestParam(defaultValue="PENDING") String memberStatus,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="50") int size) {
        if (!permissionService.isSystemAdmin() && !permissionService.isRoomAdmin()) throw new com.multichat.common.exception.PermissionDeniedException();
        var rows=membershipMapper.findJoinRequests(roomId,userId,memberStatus,permissionService.isSystemAdmin()?null:permissionService.currentActorId(),size+1,(page-1)*size); boolean more=rows.size()>size;
        return ResponseEntity.ok(ApiResponse.success(new RoomPage<>(more?rows.subList(0,size):rows,page,size,more)));
    }

    @PostMapping("/{membershipId}/approve")
    public ResponseEntity<ApiResponse<RoomMembership>> approve(@PathVariable UUID membershipId) {
        return ResponseEntity.ok(ApiResponse.success(memberService.approve(membershipId, permissionService.currentActorId())));
    }

    @PostMapping("/{membershipId}/reject")
    public ResponseEntity<ApiResponse<RoomMembership>> reject(@PathVariable UUID membershipId) {
        return ResponseEntity.ok(ApiResponse.success(memberService.reject(membershipId, permissionService.currentActorId())));
    }
}
