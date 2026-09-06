package com.multichat.audit.service;

import com.multichat.audit.entity.AuditLog;
import com.multichat.infrastructure.mapper.AuditLogMapper;
import com.multichat.audit.service.dto.AuditPage;
import com.multichat.audit.service.dto.AuditQuery;
import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import com.multichat.common.web.RequestIdFilter;
import com.multichat.permission.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DefaultAuditService implements AuditService {
    private static final Set<String> ACTIONS = Set.of(
            "LOGIN", "JOIN_ROOM", "LEAVE_ROOM", "JOIN_APPROVE", "JOIN_REJECT", "MESSAGE_SUBMIT",
            "MESSAGE_APPROVE", "MESSAGE_REJECT", "MESSAGE_TIMEOUT", "ROOM_CREATE", "ROOM_UPDATE",
            "ROOM_DELETE", "ADMIN_BROADCAST", "EMERGENCY_PUBLISH", "ADMIN_ROOM_PERMISSION_GRANT",
            "ADMIN_ROOM_PERMISSION_REVOKE");
    private final AuditLogMapper auditLogMapper;
    private final PermissionService permissionService;

    public DefaultAuditService(AuditLogMapper auditLogMapper, PermissionService permissionService) {
        this.auditLogMapper = auditLogMapper;
        this.permissionService = permissionService;
    }

    @Override
    public void append(AuditLog auditLog) {
        if (auditLog == null || auditLog.action() == null || !ACTIONS.contains(auditLog.action())
                || auditLog.resourceType() == null || auditLog.resourceId() == null) {
            throw new IllegalArgumentException("A complete audit event is required.");
        }
        AuditLog completed = new AuditLog(auditLog.id() == null ? UUID.randomUUID() : auditLog.id(),
                auditLog.requestId() == null ? requestId() : auditLog.requestId(), auditLog.actorId(), auditLog.action(),
                auditLog.resourceType(), auditLog.resourceId(), auditLog.roomId(), auditLog.messageId(),
                auditLog.beforeState(), auditLog.afterState(),
                auditLog.detail() == null ? Map.of() : auditLog.detail(),
                auditLog.createdAt() == null ? Instant.now() : auditLog.createdAt());
        auditLogMapper.insert(completed);
    }

    @Override
    @Transactional(readOnly = true)
    public AuditPage query(AuditQuery query) {
        permissionService.requireSystemAdmin();
        if (query == null || query.page() < 1 || query.size() < 1 || query.size() > 100
                || (query.from() != null && query.to() != null && query.from().isAfter(query.to()))
                || (query.action() != null && !ACTIONS.contains(query.action()))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        List<AuditLog> rows = auditLogMapper.find(query.actorId(), query.roomId(), query.messageId(), query.action(),
                query.from(), query.to(), query.size() + 1, (query.page() - 1) * query.size());
        boolean hasNext = rows.size() > query.size();
        return new AuditPage(hasNext ? rows.subList(0, query.size()) : rows, query.page(), query.size(), hasNext);
    }

    private UUID requestId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            Object value = request.getAttribute(RequestIdFilter.HEADER);
            if (value instanceof String id) {
                try { return UUID.fromString(id); } catch (IllegalArgumentException ignored) { }
            }
        }
        return null;
    }
}
