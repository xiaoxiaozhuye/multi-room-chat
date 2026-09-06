package com.multichat.member.service;

import com.multichat.audit.service.AuditService;
import com.multichat.common.exception.BusinessException;
import com.multichat.infrastructure.mapper.ChatRoomMapper;
import com.multichat.infrastructure.mapper.RoomMembershipMapper;
import com.multichat.member.entity.RoomMembership;
import com.multichat.permission.PermissionService;
import com.multichat.room.entity.ChatRoom;
import com.multichat.websocket.WebSocketSessionRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultMemberServiceTest {
    @Mock private ChatRoomMapper roomMapper;
    @Mock private RoomMembershipMapper membershipMapper;
    @Mock private AuditService auditService;
    @Mock private PermissionService permissionService;
    @Mock private WebSocketSessionRegistry sessionRegistry;

    @Test
    void openJoinLocksRoomBeforeCheckingAndConsumingTheLastSlot() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(roomMapper.findActiveByIdForUpdate(roomId)).thenReturn(Optional.of(room(roomId, "OPEN", 1)));
        when(membershipMapper.findLive(userId, roomId)).thenReturn(Optional.empty());
        when(membershipMapper.countActiveByRoomId(roomId)).thenReturn(0);

        RoomMembership membership = service().join(roomId, userId);

        assertEquals("ACTIVE", membership.status());
        InOrder order = inOrder(roomMapper, membershipMapper);
        order.verify(roomMapper).findActiveByIdForUpdate(roomId);
        order.verify(membershipMapper).findLive(userId, roomId);
        order.verify(membershipMapper).countActiveByRoomId(roomId);
        order.verify(membershipMapper).insert(any(RoomMembership.class));
        verify(auditService).append(any());
    }

    @Test
    void approvalChecksCapacityWhileHoldingTheSameRoomLock() {
        UUID roomId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID applicant = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        RoomMembership pending = new RoomMembership(membershipId, applicant, roomId, "PENDING", Instant.now(), null, 0);
        when(membershipMapper.findById(membershipId)).thenReturn(Optional.of(pending));
        when(roomMapper.findActiveByIdForUpdate(roomId)).thenReturn(Optional.of(room(roomId, "APPROVAL", 1)));
        when(membershipMapper.findByIdForUpdate(membershipId)).thenReturn(Optional.of(pending));
        when(membershipMapper.countActiveByRoomId(roomId)).thenReturn(1);

        BusinessException error = assertThrows(BusinessException.class, () -> service().approve(membershipId, admin));

        assertEquals("ROOM_FULL", error.code());
        verify(membershipMapper, never()).approvePending(eq(membershipId), any(), any());
        verify(auditService, never()).append(any());
    }

    @Test
    void approvalDoesNotCountPendingApplicationsAsActiveSlots() {
        UUID roomId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID applicant = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        RoomMembership pending = new RoomMembership(membershipId, applicant, roomId, "PENDING", Instant.now(), null, 0);
        when(membershipMapper.findById(membershipId)).thenReturn(Optional.of(pending));
        when(roomMapper.findActiveByIdForUpdate(roomId)).thenReturn(Optional.of(room(roomId, "APPROVAL", 1)));
        when(membershipMapper.findByIdForUpdate(membershipId)).thenReturn(Optional.of(pending));
        when(membershipMapper.countActiveByRoomId(roomId)).thenReturn(0);
        when(membershipMapper.approvePending(eq(membershipId), eq(admin), any())).thenReturn(1);

        RoomMembership approved = service().approve(membershipId, admin);

        assertEquals("ACTIVE", approved.status());
        verify(membershipMapper).approvePending(eq(membershipId), eq(admin), any());
        verify(auditService).append(any());
    }

    @Test
    void leaveMarksMembershipExitedAndDropsRealtimeSubscription() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        RoomMembership active = new RoomMembership(membershipId, userId, roomId, "ACTIVE", Instant.now(), Instant.now(), 4);
        when(roomMapper.findActiveByIdForUpdate(roomId)).thenReturn(Optional.of(room(roomId, "OPEN", 2)));
        when(membershipMapper.findActive(userId, roomId)).thenReturn(Optional.of(active));
        when(membershipMapper.exitActive(eq(roomId), eq(userId), any())).thenReturn(1);

        RoomMembership exited = service().leave(roomId, userId);

        assertEquals("EXITED", exited.status());
        verify(sessionRegistry).unsubscribeUserFromRoom(userId, roomId);
        verify(auditService).append(any());
    }

    private DefaultMemberService service() {
        return new DefaultMemberService(roomMapper, membershipMapper, auditService, permissionService, sessionRegistry);
    }

    private ChatRoom room(UUID roomId, String joinMode, int maxMembers) {
        Instant now = Instant.now();
        return new ChatRoom(roomId, "Room", null, maxMembers, joinMode, "ACTIVE", UUID.randomUUID(), 0, now, now);
    }
}
