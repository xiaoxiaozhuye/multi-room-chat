package com.multichat.member.controller;

import com.multichat.common.api.ApiResponse;
import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import com.multichat.infrastructure.mapper.RoomMembershipMapper;
import com.multichat.member.dto.MyRoomMembership;
import com.multichat.member.dto.MyRoomMembershipRow;
import com.multichat.permission.PermissionService;
import com.multichat.room.dto.RoomPage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/** Lists memberships owned by the authenticated user. */
@Validated
@RestController
@RequestMapping("/api/v1/users/me/rooms")
public class MyRoomController {
    private static final Set<String> MEMBER_STATUSES = Set.of("PENDING", "ACTIVE", "REJECTED", "EXITED");

    private final RoomMembershipMapper membershipMapper;
    private final PermissionService permissionService;

    public MyRoomController(RoomMembershipMapper membershipMapper, PermissionService permissionService) {
        this.membershipMapper = membershipMapper;
        this.permissionService = permissionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<RoomPage<MyRoomMembership>>> list(
            @RequestParam(required = false) String memberStatus,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        String status = memberStatus == null || memberStatus.isBlank() ? null : memberStatus.trim().toUpperCase();
        if (status != null && !MEMBER_STATUSES.contains(status)) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        List<MyRoomMembershipRow> fetched = membershipMapper.findByUser(permissionService.currentActorId(), status,
                size + 1, (page - 1) * size);
        boolean hasNext = fetched.size() > size;
        List<MyRoomMembership> items = (hasNext ? fetched.subList(0, size) : fetched).stream().map(this::toItem).toList();
        return ResponseEntity.ok(ApiResponse.success(new RoomPage<>(items, page, size, hasNext)));
    }

    private MyRoomMembership toItem(MyRoomMembershipRow row) {
        MyRoomMembership.RoomSummary room = row.summaryRoomId() == null ? null
                : new MyRoomMembership.RoomSummary(row.summaryRoomId(), row.roomName(), row.roomDescription());
        return new MyRoomMembership(row.membershipId(), row.userId(), row.roomId(), row.memberStatus(),
                row.createdAt(), row.joinedAt(), room);
    }
}
