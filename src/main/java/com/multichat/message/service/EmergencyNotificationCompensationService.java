package com.multichat.message.service;

import com.multichat.infrastructure.mapper.EmergencyNotificationCompensationMapper;
import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.common.logging.BusinessLogger;
import com.multichat.message.entity.ChatMessage;
import com.multichat.websocket.RoomMessageNotifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Delivers one persisted emergency event and preserves retry state on failure. */
@Service
public class EmergencyNotificationCompensationService {
    private final MessageMapper messageMapper;
    private final EmergencyNotificationCompensationMapper compensationMapper;
    private final RoomMessageNotifier roomMessageNotifier;
    private final BusinessLogger businessLogger;

    public EmergencyNotificationCompensationService(MessageMapper messageMapper,
                                                    EmergencyNotificationCompensationMapper compensationMapper,
                                                    RoomMessageNotifier roomMessageNotifier) {
        this(messageMapper, compensationMapper, roomMessageNotifier, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EmergencyNotificationCompensationService(MessageMapper messageMapper,
                                                    EmergencyNotificationCompensationMapper compensationMapper,
                                                    RoomMessageNotifier roomMessageNotifier,
                                                    BusinessLogger businessLogger) {
        this.messageMapper = messageMapper;
        this.compensationMapper = compensationMapper;
        this.roomMessageNotifier = roomMessageNotifier;
        this.businessLogger = businessLogger;
    }

    @Transactional
    public boolean deliver(UUID messageId) {
        ChatMessage message = messageMapper.findById(messageId).orElse(null);
        if (message == null || !"SYSTEM_NOTIFICATION".equals(message.messageType())
                || !"PUBLISHED".equals(message.status())) {
            compensationMapper.remove(messageId);
            return true;
        }
        log("EMERGENCY_COMPENSATION_ATTEMPT", message);
        if (roomMessageNotifier.publishEmergency(message)) {
            compensationMapper.remove(messageId);
            log("EMERGENCY_COMPENSATION_COMPLETED", message);
            return true;
        }
        compensationMapper.recordFailure(messageId, Instant.now());
        log("EMERGENCY_COMPENSATION_RETRY_SCHEDULED", message);
        return false;
    }

    private void log(String event, ChatMessage message) {
        if (businessLogger != null && message != null) {
            businessLogger.messageLifecycle(event, message.requestId(), message.id(), message.roomId(), message.senderId());
        }
    }
}
