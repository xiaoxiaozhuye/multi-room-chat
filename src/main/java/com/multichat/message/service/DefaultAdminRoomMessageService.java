package com.multichat.message.service;

import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.message.dto.AdminRoomMessageItemResult;
import com.multichat.message.dto.AdminRoomMessageRequest;
import com.multichat.message.dto.BroadcastResult;
import com.multichat.message.dto.EmergencyNotificationResult;
import com.multichat.message.entity.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Coordinates a multi-room command while committing each room independently. */
@Service
public class DefaultAdminRoomMessageService implements AdminRoomMessageService {
    private final AdminRoomMessageTransactionProcessor transactionProcessor;
    private final MessageContentValidator contentValidator;
    private final RoomPublishService roomPublishService;
    private final EmergencyNotificationCompensationService emergencyCompensationService;
    private final MessageMapper messageMapper;

    public DefaultAdminRoomMessageService(AdminRoomMessageTransactionProcessor transactionProcessor,
                                          MessageContentValidator contentValidator,
                                          RoomPublishService roomPublishService,
                                          EmergencyNotificationCompensationService emergencyCompensationService,
                                          MessageMapper messageMapper) {
        this.transactionProcessor = transactionProcessor;
        this.contentValidator = contentValidator;
        this.roomPublishService = roomPublishService;
        this.emergencyCompensationService = emergencyCompensationService;
        this.messageMapper = messageMapper;
    }

    @Override
    public BroadcastResult broadcast(AdminRoomMessageRequest request) {
        List<UUID> roomIds = validateAndDeduplicate(request);
        UUID broadcastId = UUID.randomUUID();
        List<AdminRoomMessageItemResult> results = new ArrayList<>(roomIds.size());
        for (UUID roomId : roomIds) {
            try {
                ChatMessage created = transactionProcessor.createAdminMessage(roomId, request.content(), broadcastId);
                RoomPublishService.RoomPublicationAttempt publication =
                        roomPublishService.publishAvailable(roomId, created.id());
                ChatMessage current = currentMessage(created);
                results.add(normalResult(current, publication.targetPendingCompensation()));
            } catch (BusinessException exception) {
                results.add(failure(roomId, exception.code()));
            }
        }
        return new BroadcastResult(broadcastId, List.copyOf(results));
    }

    @Override
    public EmergencyNotificationResult publishEmergencyNotification(AdminRoomMessageRequest request) {
        List<UUID> roomIds = validateAndDeduplicate(request);
        UUID notificationId = UUID.randomUUID();
        List<AdminRoomMessageItemResult> results = new ArrayList<>(roomIds.size());
        for (UUID roomId : roomIds) {
            try {
                ChatMessage created = transactionProcessor.createEmergencyNotification(roomId, request.content(), notificationId);
                boolean delivered = emergencyCompensationService.deliver(created.id());
                results.add(emergencyResult(currentMessage(created), delivered));
            } catch (BusinessException exception) {
                results.add(failure(roomId, exception.code()));
            }
        }
        return new EmergencyNotificationResult(notificationId, List.copyOf(results));
    }

    private List<UUID> validateAndDeduplicate(AdminRoomMessageRequest request) {
        if (request == null || request.roomIds() == null || request.roomIds().isEmpty()
                || request.roomIds().stream().anyMatch(id -> id == null)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        contentValidator.requireSendable(request.content());
        List<UUID> distinctRooms = List.copyOf(new LinkedHashSet<>(request.roomIds()));
        if (distinctRooms.size() > 100) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        return distinctRooms;
    }

    private ChatMessage currentMessage(ChatMessage created) {
        return messageMapper.findById(created.id())
                .orElseThrow(() -> new IllegalStateException("Administrative message disappeared after write"));
    }

    private AdminRoomMessageItemResult normalResult(ChatMessage message, boolean pendingCompensation) {
        return new AdminRoomMessageItemResult(message.roomId(),
                pendingCompensation ? "PENDING_COMPENSATION" : "SUCCESS", message.id(), message.roomSeq(), null,
                message.status(), null);
    }

    private AdminRoomMessageItemResult emergencyResult(ChatMessage message, boolean delivered) {
        return new AdminRoomMessageItemResult(message.roomId(), delivered ? "SUCCESS" : "PENDING_COMPENSATION",
                message.id(), null, message.notificationSeq(), message.status(), null);
    }

    private AdminRoomMessageItemResult failure(UUID roomId, String errorCode) {
        if (ErrorCode.ROOM_ACCESS_DENIED.name().equals(errorCode) || ErrorCode.FORBIDDEN.name().equals(errorCode)) {
            return AdminRoomMessageItemResult.noPermission(roomId);
        }
        return AdminRoomMessageItemResult.roomNotSendable(roomId, errorCode);
    }
}
