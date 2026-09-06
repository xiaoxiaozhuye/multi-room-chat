package com.multichat.permission;

import com.multichat.auth.entity.UserAccount;
import com.multichat.common.exception.BusinessException;
import com.multichat.infrastructure.mapper.AdminRoomPermissionMapper;
import com.multichat.infrastructure.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultPermissionServiceTest {
    @Mock private UserMapper userMapper;
    @Mock private AdminRoomPermissionMapper authorizationMapper;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void systemAdminHasEveryRoomCapabilityWithoutAnExplicitGrant() {
        UUID actorId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        authenticate(actorId);
        when(userMapper.findActiveById(actorId)).thenReturn(Optional.of(account(actorId, "SYSTEM_ADMIN")));

        assertDoesNotThrow(() -> service().requireRoomPermission(roomId, AdminOperation.MESSAGE_REVIEW));
        verify(userMapper).findActiveById(actorId);
    }

    @Test
    void roomAdminIsRejectedForAnUnassignedRoom() {
        UUID actorId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        authenticate(actorId);
        when(userMapper.findActiveById(actorId)).thenReturn(Optional.of(account(actorId, "ROOM_ADMIN")));
        when(authorizationMapper.hasActivePermission(actorId, roomId)).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().requireRoomPermission(roomId, AdminOperation.JOIN_APPROVAL));

        assertEquals("ROOM_ACCESS_DENIED", error.code());
    }

    @Test
    void roomAdminCanOperateOnlyWhenTheDatabaseGrantIsActive() {
        UUID actorId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        authenticate(actorId);
        when(userMapper.findActiveById(actorId)).thenReturn(Optional.of(account(actorId, "ROOM_ADMIN")));
        when(authorizationMapper.hasActivePermission(actorId, roomId)).thenReturn(true);

        assertDoesNotThrow(() -> service().requireRoomPermission(roomId, AdminOperation.EMERGENCY_NOTIFICATION));
        verify(authorizationMapper).hasActivePermission(actorId, roomId);
    }

    private DefaultPermissionService service() {
        return new DefaultPermissionService(userMapper, authorizationMapper);
    }

    private void authenticate(UUID actorId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null, List.of()));
    }

    private UserAccount account(UUID id, String role) {
        return new UserAccount(id, "admin", "admin@example.test", "hash", role, "ACTIVE", Instant.now(), Instant.now());
    }
}
