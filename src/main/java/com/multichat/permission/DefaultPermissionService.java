package com.multichat.permission;

import com.multichat.auth.entity.UserAccount;
import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import com.multichat.common.exception.PermissionDeniedException;
import com.multichat.infrastructure.mapper.AdminRoomPermissionMapper;
import com.multichat.infrastructure.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DefaultPermissionService implements PermissionService {
    private final UserMapper userMapper;
    private final AdminRoomPermissionMapper adminRoomPermissionMapper;

    public DefaultPermissionService(UserMapper userMapper, AdminRoomPermissionMapper adminRoomPermissionMapper) {
        this.userMapper = userMapper;
        this.adminRoomPermissionMapper = adminRoomPermissionMapper;
    }

    @Override
    public UUID currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
    }

    @Override
    public void requireSystemAdmin() {
        if (!isSystemAdmin()) {
            throw new PermissionDeniedException();
        }
    }

    @Override
    public boolean isSystemAdmin() {
        return "SYSTEM_ADMIN".equals(currentActor().role());
    }

    @Override
    public boolean isRoomAdmin() {
        return "ROOM_ADMIN".equals(currentActor().role());
    }

    @Override
    public void requireRoomPermission(UUID roomId, AdminOperation operation) {
        if (!hasRoomPermission(roomId, operation)) {
            throw new PermissionDeniedException(ErrorCode.ROOM_ACCESS_DENIED.name(), ErrorCode.ROOM_ACCESS_DENIED.message());
        }
    }

    @Override
    public boolean hasRoomPermission(UUID roomId, AdminOperation operation) {
        if (roomId == null || operation == null) return false;
        UserAccount actor = currentActor();
        if ("SYSTEM_ADMIN".equals(actor.role())) return true;
        return "ROOM_ADMIN".equals(actor.role())
                && adminRoomPermissionMapper.hasActivePermission(actor.id(), roomId);
    }

    private UserAccount currentActor() {
        UUID actorId = currentActorId();
        return userMapper.findActiveById(actorId)
                .orElseThrow(() -> new BusinessException("UNAUTHENTICATED", "Authentication is required.", HttpStatus.UNAUTHORIZED));
    }
}
