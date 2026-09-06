package com.multichat.message.service;

import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import com.multichat.common.exception.PermissionDeniedException;
import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.message.dto.BatchReviewItemResult;
import com.multichat.message.dto.BatchReviewRequest;
import com.multichat.message.dto.BatchReviewResponse;
import com.multichat.message.dto.ReviewMessagePage;
import com.multichat.message.dto.ReviewMessageQuery;
import com.multichat.message.entity.ChatMessage;
import com.multichat.permission.AdminOperation;
import com.multichat.permission.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DefaultMessageReviewService implements MessageReviewService {
    private static final Set<String> REVIEW_STATUSES = Set.of(
            "PENDING_REVIEW", "APPROVED", "PUBLISHED", "REJECTED", "TIMEOUT", "CANCELLED_BY_ROOM_DELETION");
    private final MessageMapper messageMapper;
    private final PermissionService permissionService;
    private final MessageReviewTransactionProcessor transactionProcessor;

    public DefaultMessageReviewService(MessageMapper messageMapper, PermissionService permissionService,
                                       MessageReviewTransactionProcessor transactionProcessor) {
        this.messageMapper = messageMapper;
        this.permissionService = permissionService;
        this.transactionProcessor = transactionProcessor;
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewMessagePage query(ReviewMessageQuery query) {
        if (query == null || query.page() < 1 || query.size() < 1 || query.size() > 100
                || (query.createdFrom() != null && query.createdTo() != null && query.createdFrom().isAfter(query.createdTo()))
                || (query.messageStatus() != null && !REVIEW_STATUSES.contains(query.messageStatus()))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        boolean systemAdmin = permissionService.isSystemAdmin();
        if (!systemAdmin && !permissionService.isRoomAdmin()) {
            throw new PermissionDeniedException();
        }
        if (query.roomId() != null) {
            permissionService.requireRoomPermission(query.roomId(), AdminOperation.MESSAGE_REVIEW);
        }
        UUID authorizedAdminId = systemAdmin ? null : permissionService.currentActorId();
        List<ChatMessage> messages = messageMapper.findReviewMessages(query.roomId(), query.senderId(), query.messageStatus(),
                query.createdFrom(), query.createdTo(), authorizedAdminId, query.size() + 1, (query.page() - 1) * query.size());
        boolean hasNext = messages.size() > query.size();
        return new ReviewMessagePage(hasNext ? messages.subList(0, query.size()) : messages, query.page(), query.size(), hasNext);
    }

    @Override
    public ChatMessage approve(UUID messageId) {
        return transactionProcessor.review(requireMessageId(messageId), ReviewAction.APPROVE);
    }

    @Override
    public ChatMessage reject(UUID messageId) {
        return transactionProcessor.review(requireMessageId(messageId), ReviewAction.REJECT);
    }

    @Override
    public BatchReviewResponse batch(BatchReviewRequest request) {
        ReviewAction action = request == null ? null : ReviewAction.parse(request.action());
        if (action == null || request.messageIds() == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>(request.messageIds());
        if (uniqueIds.isEmpty() || uniqueIds.size() > 100 || uniqueIds.contains(null)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return new BatchReviewResponse(uniqueIds.stream().map(id -> batchItem(id, action)).toList());
    }

    private BatchReviewItemResult batchItem(UUID messageId, ReviewAction action) {
        try {
            ChatMessage message = transactionProcessor.review(messageId, action);
            return new BatchReviewItemResult(messageId.toString(), action.targetStatus(), message.status(), null);
        } catch (BusinessException exception) {
            String result = switch (exception.code()) {
                case "MESSAGE_NOT_FOUND" -> "NOT_FOUND";
                case "ROOM_ACCESS_DENIED", "FORBIDDEN" -> "NO_PERMISSION";
                case "REVIEW_ALREADY_PROCESSED" -> "ALREADY_PROCESSED";
                default -> "FAILED";
            };
            return new BatchReviewItemResult(messageId.toString(), result, null, exception.code());
        } catch (RuntimeException exception) {
            // Each item is a separate transaction.  A storage failure for one
            // row must not prevent the independently valid rows from running.
            return new BatchReviewItemResult(messageId.toString(), "FAILED", null, ErrorCode.INTERNAL_ERROR.name());
        }
    }

    private UUID requireMessageId(UUID messageId) {
        if (messageId == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        return messageId;
    }
}
