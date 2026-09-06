package com.multichat.websocket;

import com.multichat.auth.entity.UserAccount;
import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import com.multichat.infrastructure.mapper.AdminRoomPermissionMapper;
import com.multichat.infrastructure.mapper.RoomMembershipMapper;
import com.multichat.infrastructure.mapper.UserMapper;
import com.multichat.room.RoomStatePolicy;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** The authoritative check for both initial subscription and later delivery. */
@Component
public class RoomSubscriptionAuthorizer {
    private final RoomStatePolicy roomStatePolicy;
    private final RoomMembershipMapper membershipMapper;
    private final UserMapper userMapper;
    private final AdminRoomPermissionMapper permissionMapper;

    public RoomSubscriptionAuthorizer(RoomStatePolicy roomStatePolicy, RoomMembershipMapper membershipMapper,
                                     UserMapper userMapper, AdminRoomPermissionMapper permissionMapper) {
        this.roomStatePolicy = roomStatePolicy;
        this.membershipMapper = membershipMapper;
        this.userMapper = userMapper;
        this.permissionMapper = permissionMapper;
    }

    public void requireSubscribable(UUID userId, UUID roomId) {
        roomStatePolicy.requireSubscribable(roomId);
        UserAccount user = userMapper.findActiveById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        if (membershipMapper.findActive(userId, roomId).isPresent()) return;
        if ("SYSTEM_ADMIN".equals(user.role())) return;
        if ("ROOM_ADMIN".equals(user.role()) && permissionMapper.hasActivePermission(userId, roomId)) return;
        throw new BusinessException(ErrorCode.ROOM_ACCESS_DENIED);
    }
}
