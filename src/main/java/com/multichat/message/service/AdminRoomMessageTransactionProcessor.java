package com.multichat.message.service;

import com.multichat.audit.AuditStates;
import com.multichat.audit.entity.AuditLog;
import com.multichat.audit.service.AuditService;
import com.multichat.common.logging.BusinessLogger;
import com.multichat.infrastructure.mapper.EmergencyNotificationCompensationMapper;
import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.message.entity.ChatMessage;
import com.multichat.permission.AdminOperation;
import com.multichat.permission.PermissionService;
import com.multichat.room.RoomStatePolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** One room write transaction in a multi-room administrator operation. */
@Service
public class AdminRoomMessageTransactionProcessor {
    private final MessageMapper messageMapper;
    private final EmergencyNotificationCompensationMapper emergencyCompensationMapper;
    private final PermissionService permissionService;
    private final RoomStatePolicy roomStatePolicy;
    private final AuditService auditService;
    private final BusinessLogger businessLogger;

    public AdminRoomMessageTransactionProcessor(MessageMapper messageMapper,
                                                EmergencyNotificationCompensationMapper emergencyCompensationMapper,
                                                PermissionService permissionService, RoomStatePolicy roomStatePolicy,
                                                AuditService auditService) {
        this(messageMapper, emergencyCompensationMapper, permissionService, roomStatePolicy, auditService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AdminRoomMessageTransactionProcessor(MessageMapper messageMapper,
                                                EmergencyNotificationCompensationMapper emergencyCompensationMapper,
                                                PermissionService permissionService, RoomStatePolicy roomStatePolicy,
                                                AuditService auditService, BusinessLogger businessLogger) {
        this.messageMapper = messageMapper;
        this.emergencyCompensationMapper = emergencyCompensationMapper;
        this.permissionService = permissionService;
        this.roomStatePolicy = roomStatePolicy;
        this.auditService = auditService;
        this.businessLogger = businessLogger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatMessage createAdminMessage(UUID roomId, String content, UUID operationId) {
        permissionService.requireRoomPermission(roomId, AdminOperation.ADMIN_BROADCAST);
        roomStatePolicy.requireAdminMessageAllowed(roomId);
        UUID actorId = permissionService.currentActorId();
        Instant now = Instant.now();
        ChatMessage candidate = new ChatMessage(UUID.randomUUID(), UUID.randomUUID(), roomId, actorId,
                null, null, "ADMIN_MESSAGE", content, "APPROVED", null, null, null, 0, now);
        requireInserted(messageMapper.insertApprovedAdminMessage(candidate));
        ChatMessage saved = requireMessage(candidate.id());
        auditService.append(new AuditLog(UUID.randomUUID(), null, actorId, "ADMIN_BROADCAST", "MESSAGE",
                saved.id(), saved.roomId(), saved.id(), null, AuditStates.message(saved),
                AuditStates.detail("broadcastId", operationId, "messageType", saved.messageType()), now));
        log("ADMIN_MESSAGE_PERSISTED", saved, actorId);
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatMessage createEmergencyNotification(UUID roomId, String content, UUID operationId) {
        permissionService.requireRoomPermission(roomId, AdminOperation.EMERGENCY_NOTIFICATION);
        roomStatePolicy.requireAdminMessageAllowed(roomId);
        UUID actorId = permissionService.currentActorId();
        Instant now = Instant.now();
        ChatMessage candidate = new ChatMessage(UUID.randomUUID(), UUID.randomUUID(), roomId, actorId,
                null, null, "SYSTEM_NOTIFICATION", content, "PUBLISHED", null, null, now, 0, now);
        requireInserted(messageMapper.insertPublishedEmergencyNotification(candidate));
        ChatMessage saved = requireMessage(candidate.id());
        requireInserted(emergencyCompensationMapper.enqueue(saved.id(), now));
        auditService.append(new AuditLog(UUID.randomUUID(), null, actorId, "EMERGENCY_PUBLISH", "MESSAGE",
                saved.id(), saved.roomId(), saved.id(), null, AuditStates.message(saved),
                AuditStates.detail("notificationId", operationId, "messageType", saved.messageType()), now));
        log("EMERGENCY_NOTIFICATION_PERSISTED_AND_ENQUEUED", saved, actorId);
        return saved;
    }

    private ChatMessage requireMessage(UUID messageId) {
        return messageMapper.findById(messageId)
                .orElseThrow(() -> new IllegalStateException("Inserted administrative message is missing"));
    }

    private void requireInserted(int affectedRows) {
        if (affectedRows != 1) throw new IllegalStateException("Administrative message write did not affect one row");
    }

    private void log(String event, ChatMessage message, UUID actorId) {
        if (businessLogger != null) {
            businessLogger.messageLifecycle(event, message.requestId(), message.id(), message.roomId(), actorId);
        }
    }
}
