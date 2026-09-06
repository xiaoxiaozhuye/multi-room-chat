package com.multichat.member.service;

import com.multichat.audit.entity.AuditLog;
import com.multichat.audit.AuditStates;
import com.multichat.audit.service.AuditService;
import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import com.multichat.infrastructure.mapper.ChatRoomMapper;
import com.multichat.infrastructure.mapper.RoomMembershipMapper;
import com.multichat.member.entity.RoomMembership;
import com.multichat.permission.AdminOperation;
import com.multichat.permission.PermissionService;
import com.multichat.room.entity.ChatRoom;
import com.multichat.websocket.WebSocketSessionRegistry;
import com.multichat.websocket.RoomMessageNotifier;
import com.multichat.common.api.ApiError;
import com.multichat.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Membership writes always re-read room state; cached metadata is never trusted for a join. */
@Service
public class DefaultMemberService implements MemberService {
    private final ChatRoomMapper roomMapper;
    private final RoomMembershipMapper membershipMapper;
    private final AuditService auditService;
    private final PermissionService permissionService;
    private final WebSocketSessionRegistry sessionRegistry;
    private final RoomMessageNotifier roomMessageNotifier;

    public DefaultMemberService(ChatRoomMapper roomMapper, RoomMembershipMapper membershipMapper,
                                AuditService auditService, PermissionService permissionService,
                                WebSocketSessionRegistry sessionRegistry) {
        this(roomMapper, membershipMapper, auditService, permissionService, sessionRegistry, null);
    }

    @Autowired
    public DefaultMemberService(ChatRoomMapper roomMapper, RoomMembershipMapper membershipMapper,
                                AuditService auditService, PermissionService permissionService,
                                WebSocketSessionRegistry sessionRegistry, RoomMessageNotifier roomMessageNotifier) {
        this.roomMapper = roomMapper;
        this.membershipMapper = membershipMapper;
        this.auditService = auditService;
        this.permissionService = permissionService;
        this.sessionRegistry = sessionRegistry;
        this.roomMessageNotifier = roomMessageNotifier;
    }

    @Override
    @Transactional
    public RoomMembership join(UUID roomId, UUID userId) {
        // Every path that can consume an ACTIVE slot first locks this room row.
        // This serializes the count/check/insert sequence for the final slot.
        ChatRoom room = requireJoinableRoomForUpdate(roomId);
        membershipMapper.findLive(userId, roomId).ifPresent(live -> {
            throw new BusinessException("ACTIVE".equals(live.status())
                    ? ErrorCode.MEMBERSHIP_ALREADY_ACTIVE : ErrorCode.JOIN_REQUEST_ALREADY_PENDING);
        });
        Instant now = Instant.now();
        String membershipStatus = "OPEN".equals(room.joinMode()) ? "ACTIVE" : "PENDING";
        if ("ACTIVE".equals(membershipStatus) && membershipMapper.countActiveByRoomId(roomId) >= room.maxMembers()) {
            throw new BusinessException(ErrorCode.ROOM_FULL);
        }
        RoomMembership membership = new RoomMembership(UUID.randomUUID(), userId, roomId, membershipStatus,
                now, "ACTIVE".equals(membershipStatus) ? now : null, 0);
        membershipMapper.insert(membership);
        auditService.append(new AuditLog(UUID.randomUUID(), null, userId, "JOIN_ROOM", "MEMBERSHIP",
                membership.id(), roomId, null, null, AuditStates.membership(membership),
                AuditStates.detail("joinMode", room.joinMode()), now));
        return membership;
    }

    @Override
    @Transactional
    public RoomMembership leave(UUID roomId, UUID userId) {
        // Locking also makes a concurrently freed slot visible to the next join.
        requireExistingRoomForUpdate(roomId);
        RoomMembership active = membershipMapper.findActive(userId, roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_ACCESS_DENIED));
        Instant now = Instant.now();
        if (membershipMapper.exitActive(roomId, userId, now) != 1) {
            throw new BusinessException(ErrorCode.ROOM_ACCESS_DENIED);
        }
        RoomMembership exited = new RoomMembership(active.id(), userId, roomId, "EXITED", active.requestedAt(), active.activatedAt(),
                active.version() + 1);
        auditService.append(new AuditLog(UUID.randomUUID(), null, userId, "LEAVE_ROOM", "MEMBERSHIP",
                active.id(), roomId, null, AuditStates.membership(active), AuditStates.membership(exited), Map.of(), now));
        unsubscribeAfterCommit(userId, roomId);
        return exited;
    }

    @Override
    @Transactional
    public RoomMembership approve(UUID membershipId, UUID actorId) {
        RoomMembership request = membershipMapper.findById(membershipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_REQUEST_NOT_FOUND));
        permissionService.requireRoomPermission(request.roomId(), AdminOperation.JOIN_APPROVAL);

        ChatRoom room = requireJoinableRoomForUpdate(request.roomId());
        RoomMembership pending = membershipMapper.findByIdForUpdate(membershipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_REQUEST_NOT_FOUND));
        if (!"PENDING".equals(pending.status())) {
            throw new BusinessException(ErrorCode.MEMBER_REQUEST_ALREADY_PROCESSED);
        }
        // PENDING records deliberately do not count toward max_members.
        if (membershipMapper.countActiveByRoomId(room.id()) >= room.maxMembers()) {
            throw new BusinessException(ErrorCode.ROOM_FULL);
        }

        Instant now = Instant.now();
        if (membershipMapper.approvePending(membershipId, actorId, now) != 1) {
            throw new BusinessException(ErrorCode.MEMBER_REQUEST_ALREADY_PROCESSED);
        }
        RoomMembership approved = new RoomMembership(pending.id(), pending.userId(), pending.roomId(), "ACTIVE", pending.requestedAt(), now,
                pending.version() + 1);
        auditService.append(new AuditLog(UUID.randomUUID(), null, actorId, "JOIN_APPROVE", "MEMBERSHIP",
                membershipId, room.id(), null, AuditStates.membership(pending), AuditStates.membership(approved),
                AuditStates.detail("applicantId", pending.userId()), now));
        return approved;
    }

    @Override
    @Transactional
    public RoomMembership reject(UUID membershipId, UUID actorId) {
        RoomMembership request = membershipMapper.findById(membershipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_REQUEST_NOT_FOUND));
        permissionService.requireRoomPermission(request.roomId(), AdminOperation.JOIN_APPROVAL);

        ChatRoom room = requireExistingRoomForUpdate(request.roomId());
        RoomMembership pending = membershipMapper.findByIdForUpdate(membershipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_REQUEST_NOT_FOUND));
        if (!"PENDING".equals(pending.status())) {
            throw new BusinessException(ErrorCode.MEMBER_REQUEST_ALREADY_PROCESSED);
        }
        Instant now = Instant.now();
        if (membershipMapper.rejectPending(membershipId, actorId, now) != 1) {
            throw new BusinessException(ErrorCode.MEMBER_REQUEST_ALREADY_PROCESSED);
        }
        RoomMembership rejected = new RoomMembership(pending.id(), pending.userId(), pending.roomId(), "REJECTED", pending.requestedAt(), null,
                pending.version() + 1);
        auditService.append(new AuditLog(UUID.randomUUID(), null, actorId, "JOIN_REJECT", "MEMBERSHIP",
                membershipId, room.id(), null, AuditStates.membership(pending), AuditStates.membership(rejected),
                AuditStates.detail("applicantId", pending.userId()), now));
        return rejected;
    }

    private ChatRoom requireJoinableRoomForUpdate(UUID roomId) {
        ChatRoom room = requireExistingRoomForUpdate(roomId);
        if ("PAUSED".equals(room.status())) throw new BusinessException(ErrorCode.ROOM_PAUSED);
        if ("CLOSED".equals(room.status())) throw new BusinessException(ErrorCode.ROOM_CLOSED);
        if (!"ACTIVE".equals(room.status())) throw new BusinessException(ErrorCode.ROOM_STATE_CONFLICT);
        return room;
    }

    private ChatRoom requireExistingRoomForUpdate(UUID roomId) {
        return roomMapper.findActiveByIdForUpdate(roomId).orElseGet(() -> {
            if (roomMapper.isDeleted(roomId)) throw new BusinessException(ErrorCode.ROOM_DELETED);
            throw new BusinessException(ErrorCode.ROOM_NOT_FOUND);
        });
    }

    private void unsubscribeAfterCommit(UUID userId, UUID roomId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Keeps direct service use deterministic; normal application calls are transactional.
            revokeSubscription(userId, roomId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                revokeSubscription(userId, roomId);
            }
        });
    }

    private void revokeSubscription(UUID userId, UUID roomId) {
        if (roomMessageNotifier != null) {
            roomMessageNotifier.revokeUser(userId, roomId,
                    new ApiError(ErrorCode.ROOM_ACCESS_DENIED.name(), ErrorCode.ROOM_ACCESS_DENIED.message()));
        } else {
            sessionRegistry.unsubscribeUserFromRoom(userId, roomId);
        }
    }
}
