package com.multichat.permission.service;

import com.multichat.audit.entity.AuditLog;
import com.multichat.audit.service.AuditService;
import com.multichat.auth.entity.UserAccount;
import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import com.multichat.infrastructure.mapper.AdminRoomPermissionMapper;
import com.multichat.infrastructure.mapper.ChatRoomMapper;
import com.multichat.infrastructure.mapper.UserMapper;
import com.multichat.permission.PermissionService;
import com.multichat.permission.dto.RoomAuthorizationGrantResponse;
import com.multichat.permission.dto.RoomAuthorizationResponse;
import com.multichat.permission.dto.RoomAuthorizationRevokeResponse;
import com.multichat.permission.entity.RoomAdminAuthorization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DefaultRoomAuthorizationService implements RoomAuthorizationService {
    private final PermissionService permissionService;
    private final ChatRoomMapper chatRoomMapper;
    private final UserMapper userMapper;
    private final AdminRoomPermissionMapper authorizationMapper;
    private final AuditService auditService;

    public DefaultRoomAuthorizationService(PermissionService permissionService, ChatRoomMapper chatRoomMapper,
                                           UserMapper userMapper, AdminRoomPermissionMapper authorizationMapper,
                                           AuditService auditService) {
        this.permissionService = permissionService;
        this.chatRoomMapper = chatRoomMapper;
        this.userMapper = userMapper;
        this.authorizationMapper = authorizationMapper;
        this.auditService = auditService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomAuthorizationResponse> list(UUID roomId) {
        permissionService.requireSystemAdmin();
        requireExistingRoom(roomId);
        return authorizationMapper.findActiveByRoomId(roomId).stream()
                .map(this::response).toList();
    }

    @Override
    @Transactional
    public RoomAuthorizationGrantResponse grant(UUID roomId, UUID adminUserId) {
        permissionService.requireSystemAdmin();
        requireExistingRoom(roomId);
        UserAccount grantee = userMapper.findActiveById(adminUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_ROLE_REQUIRED));
        if (!"ROOM_ADMIN".equals(grantee.role())) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_REQUIRED);
        }

        UUID actorId = permissionService.currentActorId();
        RoomAdminAuthorization authorization = authorizationMapper.findActive(adminUserId, roomId).orElse(null);
        if (authorization == null) {
            Instant now = Instant.now();
            authorization = new RoomAdminAuthorization(UUID.randomUUID(), adminUserId, roomId, actorId,
                    now, null, null, now);
            authorizationMapper.insert(authorization);
            appendAudit(actorId, "ADMIN_ROOM_PERMISSION_GRANT", authorization.id(), roomId);
        }
        return new RoomAuthorizationGrantResponse(roomId, adminUserId, authorization.grantedAt());
    }

    @Override
    @Transactional
    public RoomAuthorizationRevokeResponse revoke(UUID roomId, UUID adminUserId) {
        permissionService.requireSystemAdmin();
        requireExistingRoom(roomId);
        UUID actorId = permissionService.currentActorId();
        RoomAdminAuthorization active = authorizationMapper.findActive(adminUserId, roomId).orElse(null);
        Instant revokedAt = Instant.now();
        if (active != null && authorizationMapper.revoke(active.id(), actorId, revokedAt) == 1) {
            appendAudit(actorId, "ADMIN_ROOM_PERMISSION_REVOKE", active.id(), roomId);
        } else {
            revokedAt = authorizationMapper.findLatest(adminUserId, roomId)
                    .map(RoomAdminAuthorization::revokedAt)
                    .orElse(revokedAt);
        }
        return new RoomAuthorizationRevokeResponse(roomId, adminUserId, true, revokedAt);
    }

    private void requireExistingRoom(UUID roomId) {
        if (chatRoomMapper.findActiveById(roomId).isEmpty()) {
            throw new BusinessException(ErrorCode.ROOM_NOT_FOUND);
        }
    }

    private RoomAuthorizationResponse response(RoomAdminAuthorization authorization) {
        return new RoomAuthorizationResponse(authorization.id(), authorization.roomId(), authorization.adminId(),
                authorization.grantedBy(), authorization.createdAt());
    }

    private void appendAudit(UUID actorId, String action, UUID authorizationId, UUID roomId) {
        auditService.append(new AuditLog(UUID.randomUUID(), null, actorId, action, "ADMIN_ROOM_PERMISSION",
                authorizationId, roomId, null, Instant.now()));
    }
}
