package com.multichat.audit.service;

import com.multichat.audit.entity.AuditLog;
import com.multichat.audit.service.dto.AuditPage;
import com.multichat.audit.service.dto.AuditQuery;
import com.multichat.infrastructure.mapper.AuditLogMapper;
import com.multichat.permission.PermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultAuditServiceTest {
    private final AuditLogMapper mapper = mock(AuditLogMapper.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final DefaultAuditService service = new DefaultAuditService(mapper, permissions);

    @Test
    void appendCompletesOptionalAuditFieldsAndKeepsStructuredDetail() {
        UUID membershipId = UUID.randomUUID();
        service.append(new AuditLog(null, null, UUID.randomUUID(), "JOIN_ROOM", "MEMBERSHIP", membershipId,
                UUID.randomUUID(), null, null, null, Map.of("joinMode", "OPEN"), null));

        ArgumentCaptor<AuditLog> captured = ArgumentCaptor.forClass(AuditLog.class);
        verify(mapper).insert(captured.capture());
        assertNotNull(captured.getValue().id());
        assertNotNull(captured.getValue().createdAt());
        assertEquals(Map.of("joinMode", "OPEN"), captured.getValue().detail());
    }

    @Test
    void queryRequiresSystemAdministratorAndFiltersByMessage() {
        UUID messageId = UUID.randomUUID();
        AuditLog event = new AuditLog(UUID.randomUUID(), null, UUID.randomUUID(), "MESSAGE_SUBMIT", "MESSAGE",
                messageId, UUID.randomUUID(), messageId, Map.of(), Map.of("status", "PENDING_REVIEW"), Map.of(), Instant.now());
        when(mapper.find(isNull(), isNull(), eq(messageId), isNull(), isNull(), isNull(), eq(51), eq(0)))
                .thenReturn(List.of(event));

        AuditPage page = service.query(new AuditQuery(null, null, messageId, null, null, null, 1, 50));

        verify(permissions).requireSystemAdmin();
        assertEquals(List.of(event), page.items());
        assertFalse(page.hasNext());
    }
}
