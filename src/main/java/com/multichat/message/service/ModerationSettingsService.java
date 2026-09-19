package com.multichat.message.service;

import com.multichat.audit.AuditStates;
import com.multichat.audit.entity.AuditLog;
import com.multichat.audit.service.AuditService;
import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import com.multichat.infrastructure.mapper.RoomModerationSettingsMapper;
import com.multichat.infrastructure.mapper.RoomMembershipMapper;
import com.multichat.permission.AdminOperation;
import org.springframework.dao.DataAccessException;
import com.multichat.permission.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

/** Room-level moderation overrides. The system default is enabled. */
@Service
public class ModerationSettingsService {
    private final ModerationProperties defaults;
    private final RoomModerationSettingsMapper roomSettingsMapper;
    private final RoomMembershipMapper membershipMapper;
    private final PermissionService permissionService;
    private final AuditService auditService;

    public ModerationSettingsService(ModerationProperties defaults,
                                     RoomModerationSettingsMapper roomSettingsMapper,
                                     RoomMembershipMapper membershipMapper,
                                     PermissionService permissionService, AuditService auditService) {
        this.defaults = defaults;
        this.roomSettingsMapper = roomSettingsMapper;
        this.membershipMapper = membershipMapper;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    public boolean isEnabled(UUID roomId) {
        try {
            return roomSettingsMapper.find(roomId).orElse(defaults.enabled());
        } catch (DataAccessException ignored) {
            return defaults.enabled();
        }
    }

    public boolean isEnabledForMember(UUID roomId) {
        UUID actorId = permissionService.currentActorId();
        if (roomId == null || membershipMapper.findActive(actorId, roomId).isEmpty()) {
            throw new BusinessException(ErrorCode.ROOM_ACCESS_DENIED);
        }
        return isEnabled(roomId);
    }

    @Transactional
    public boolean updateRoom(UUID roomId, Boolean requestedEnabled) {
        if (roomId == null || requestedEnabled == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        permissionService.requireRoomPermission(roomId, AdminOperation.MESSAGE_REVIEW);

        boolean previous = isEnabled(roomId);
        UUID actorId = permissionService.currentActorId();
        Instant now = Instant.now();
        if (roomSettingsMapper.upsert(roomId, requestedEnabled, actorId, now) != 1) {
            throw new IllegalStateException("Room moderation setting was not updated");
        }
        auditService.append(new AuditLog(UUID.randomUUID(), null, actorId, "ROOM_MODERATION_SETTING_UPDATE",
                "CHAT_ROOM", roomId, roomId, null,
                AuditStates.detail("enabled", previous), AuditStates.detail("enabled", requestedEnabled),
                AuditStates.detail("settingKey", "chat.moderation.enabled", "scope", "ROOM"), now));
        return requestedEnabled;
    }

    public void requireRoomPermission(UUID roomId) {
        permissionService.requireRoomPermission(roomId, AdminOperation.MESSAGE_REVIEW);
    }

}
