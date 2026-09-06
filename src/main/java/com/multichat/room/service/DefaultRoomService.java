package com.multichat.room.service;

import com.multichat.audit.entity.AuditLog;
import com.multichat.audit.AuditStates;
import com.multichat.audit.service.AuditService;
import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import com.multichat.infrastructure.mapper.ChatRoomMapper;
import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.permission.AdminOperation;
import com.multichat.permission.PermissionService;
import com.multichat.room.dto.CreateRoomRequest;
import com.multichat.room.dto.RoomDeletionResponse;
import com.multichat.room.dto.RoomPage;
import com.multichat.room.dto.UpdateRoomRequest;
import com.multichat.room.entity.ChatRoom;
import com.multichat.websocket.RoomMessageNotifier;
import com.multichat.common.api.ApiError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DefaultRoomService implements RoomService {
    private static final Set<String> JOIN_MODES = Set.of("OPEN", "APPROVAL");
    private static final Set<String> ROOM_STATUSES = Set.of("ACTIVE", "PAUSED", "CLOSED");

    private final ChatRoomMapper roomMapper;
    private final MessageMapper messageMapper;
    private final PermissionService permissionService;
    private final RoomLookupService roomLookupService;
    private final AuditService auditService;
    private final RoomMessageNotifier roomMessageNotifier;

    public DefaultRoomService(ChatRoomMapper roomMapper, MessageMapper messageMapper,
                              PermissionService permissionService, RoomLookupService roomLookupService,
                              AuditService auditService) {
        this(roomMapper, messageMapper, permissionService, roomLookupService, auditService, null);
    }

    @Autowired
    public DefaultRoomService(ChatRoomMapper roomMapper, MessageMapper messageMapper,
                              PermissionService permissionService, RoomLookupService roomLookupService,
                              AuditService auditService, RoomMessageNotifier roomMessageNotifier) {
        this.roomMapper = roomMapper;
        this.messageMapper = messageMapper;
        this.permissionService = permissionService;
        this.roomLookupService = roomLookupService;
        this.auditService = auditService;
        this.roomMessageNotifier = roomMessageNotifier;
    }

    @Override
    @Transactional
    public ChatRoom create(UUID ignoredActorId, CreateRoomRequest request) {
        permissionService.requireSystemAdmin();
        UUID actorId = permissionService.currentActorId();
        String joinMode = enumValue(request.joinMode(), JOIN_MODES, "joinMode");
        Instant now = Instant.now();
        ChatRoom room = new ChatRoom(UUID.randomUUID(), normalizedName(request.name()), normalizeDescription(request.description()),
                request.maxMembers(), joinMode, "ACTIVE", actorId, 0, now, now);
        roomMapper.insert(room);
        auditService.append(new AuditLog(UUID.randomUUID(), null, actorId, "ROOM_CREATE", "CHAT_ROOM",
                room.id(), room.id(), null, null, AuditStates.room(room), Map.of(), now));
        return room;
    }

    @Override
    @Transactional(readOnly = true)
    public RoomPage<ChatRoom> list(String name, String roomStatus, String joinMode, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        String status = roomStatus == null || roomStatus.isBlank() ? "ACTIVE" : enumValue(roomStatus, ROOM_STATUSES, "roomStatus");
        String mode = joinMode == null || joinMode.isBlank() ? null : enumValue(joinMode, JOIN_MODES, "joinMode");
        String prefix = name == null || name.isBlank() ? null : normalizedName(name);
        List<ChatRoom> result = roomMapper.findPage(prefix, status, mode, size + 1, (page - 1) * size);
        boolean hasNext = result.size() > size;
        return new RoomPage<>(hasNext ? result.subList(0, size) : result, page, size, hasNext);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatRoom get(UUID roomId) {
        return roomLookupService.findActiveById(roomId).orElseGet(() -> {
            if (roomMapper.isDeleted(roomId)) throw new BusinessException(ErrorCode.ROOM_DELETED);
            throw new BusinessException(ErrorCode.ROOM_NOT_FOUND);
        });
    }

    @Override
    @Transactional
    public ChatRoom update(UUID roomId, UpdateRoomRequest request) {
        if (request.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        permissionService.requireRoomPermission(roomId, AdminOperation.ROOM_MANAGEMENT);
        ChatRoom current = get(roomId);
        String nextStatus = request.roomStatus() == null ? current.status()
                : enumValue(request.roomStatus(), ROOM_STATUSES, "roomStatus");
        ensureValidTransition(current.status(), nextStatus);
        String nextJoinMode = request.joinMode() == null ? current.joinMode()
                : enumValue(request.joinMode(), JOIN_MODES, "joinMode");
        Instant now = Instant.now();
        ChatRoom updated = new ChatRoom(current.id(), request.name() == null ? current.name() : normalizedName(request.name()),
                request.description() == null ? current.description() : normalizeDescription(request.description()),
                request.maxMembers() == null ? current.maxMembers() : request.maxMembers(), nextJoinMode, nextStatus,
                current.createdBy(), current.version(), current.createdAt(), now);
        if (roomMapper.update(updated) != 1) {
            // A concurrent delete/update must be retried from the current authoritative state.
            if (roomMapper.isDeleted(roomId)) throw new BusinessException(ErrorCode.ROOM_DELETED);
            throw new BusinessException(ErrorCode.ROOM_STATE_CONFLICT);
        }
        UUID actorId = permissionService.currentActorId();
        ChatRoom persisted = new ChatRoom(updated.id(), updated.name(), updated.description(), updated.maxMembers(), updated.joinMode(),
                updated.status(), updated.createdBy(), updated.version() + 1, updated.createdAt(), updated.updatedAt());
        auditService.append(new AuditLog(UUID.randomUUID(), null, actorId, "ROOM_UPDATE", "CHAT_ROOM",
                roomId, roomId, null, AuditStates.room(current), AuditStates.room(persisted), Map.of(), now));
        invalidateAfterCommit(roomId, false);
        return persisted;
    }

    @Override
    @Transactional
    public RoomDeletionResponse delete(UUID roomId) {
        permissionService.requireSystemAdmin();
        // get also makes deleted and missing rooms precise, before mutating dependent records.
        ChatRoom current = get(roomId);
        UUID actorId = permissionService.currentActorId();
        Instant now = Instant.now();
        if (roomMapper.logicalDelete(roomId, actorId, now) != 1) {
            if (roomMapper.isDeleted(roomId)) throw new BusinessException(ErrorCode.ROOM_DELETED);
            throw new BusinessException(ErrorCode.ROOM_NOT_FOUND);
        }
        int cancelledMessages = messageMapper.cancelUnpublishedByRoomDeletion(roomId, now);
        auditService.append(new AuditLog(UUID.randomUUID(), null, actorId, "ROOM_DELETE", "CHAT_ROOM",
                roomId, roomId, null, AuditStates.room(current), AuditStates.deletedRoom(current, now),
                AuditStates.detail("cancelledUnpublishedMessages", cancelledMessages), now));
        invalidateAfterCommit(roomId, true);
        return new RoomDeletionResponse(roomId, "DELETED", now);
    }

    private void invalidateAfterCommit(UUID roomId, boolean deleted) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                roomLookupService.invalidate(roomId);
                if (deleted && roomMessageNotifier != null) {
                    roomMessageNotifier.revokeRoom(roomId,
                            new ApiError(ErrorCode.ROOM_DELETED.name(), ErrorCode.ROOM_DELETED.message()));
                }
            }
        });
    }

    private void ensureValidTransition(String from, String to) {
        if (from.equals(to)) return;
        boolean valid = ("ACTIVE".equals(from) && ("PAUSED".equals(to) || "CLOSED".equals(to)))
                || ("PAUSED".equals(from) && ("ACTIVE".equals(to) || "CLOSED".equals(to)));
        if (!valid) throw new BusinessException(ErrorCode.ROOM_STATE_CONFLICT);
    }

    private String normalizedName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 120) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return name.trim();
    }

    private String normalizeDescription(String description) {
        return description == null ? null : description.trim();
    }

    private String enumValue(String value, Set<String> allowed, String field) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        return normalized;
    }
}
