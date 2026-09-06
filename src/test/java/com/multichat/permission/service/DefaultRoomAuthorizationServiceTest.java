package com.multichat.permission.service;

import com.multichat.audit.service.AuditService;
import com.multichat.auth.entity.UserAccount;
import com.multichat.common.exception.BusinessException;
import com.multichat.infrastructure.mapper.AdminRoomPermissionMapper;
import com.multichat.infrastructure.mapper.ChatRoomMapper;
import com.multichat.infrastructure.mapper.UserMapper;
import com.multichat.permission.PermissionService;
import com.multichat.permission.entity.RoomAdminAuthorization;
import com.multichat.room.entity.ChatRoom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultRoomAuthorizationServiceTest {
    @Mock private PermissionService permissionService;
    @Mock private ChatRoomMapper roomMapper;
    @Mock private UserMapper userMapper;
    @Mock private AdminRoomPermissionMapper authorizationMapper;
    @Mock private AuditService auditService;

    @Test
    void grantsOnlyToAnActiveRoomAdminAndAuditsTheGrant() {
        UUID systemAdmin = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID roomAdmin = UUID.randomUUID();
        when(roomMapper.findActiveById(roomId)).thenReturn(Optional.of(room(roomId)));
        when(userMapper.findActiveById(roomAdmin)).thenReturn(Optional.of(account(roomAdmin, "ROOM_ADMIN")));
        when(permissionService.currentActorId()).thenReturn(systemAdmin);
        when(authorizationMapper.findActive(roomAdmin, roomId)).thenReturn(Optional.empty());

        var result = service().grant(roomId, roomAdmin);

        assertEquals(roomId, result.roomId());
        assertEquals(roomAdmin, result.adminUserId());
        ArgumentCaptor<RoomAdminAuthorization> authorization = ArgumentCaptor.forClass(RoomAdminAuthorization.class);
        verify(authorizationMapper).insert(authorization.capture());
        assertEquals(systemAdmin, authorization.getValue().grantedBy());
        verify(auditService).append(any());
    }

    @Test
    void refusesToGrantToAUserWhoIsNotARoomAdmin() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(roomMapper.findActiveById(roomId)).thenReturn(Optional.of(room(roomId)));
        when(userMapper.findActiveById(userId)).thenReturn(Optional.of(account(userId, "SYSTEM_ADMIN")));

        BusinessException error = assertThrows(BusinessException.class, () -> service().grant(roomId, userId));

        assertEquals("ADMIN_ROLE_REQUIRED", error.code());
        verify(authorizationMapper, never()).insert(any());
    }

    private DefaultRoomAuthorizationService service() {
        return new DefaultRoomAuthorizationService(permissionService, roomMapper, userMapper, authorizationMapper, auditService);
    }

    private ChatRoom room(UUID id) {
        Instant now = Instant.now();
        return new ChatRoom(id, "room", null, 10, "OPEN", "ACTIVE", UUID.randomUUID(), 0, now, now);
    }

    private UserAccount account(UUID id, String role) {
        Instant now = Instant.now();
        return new UserAccount(id, "admin", "admin@example.test", "hash", role, "ACTIVE", now, now);
    }
}
