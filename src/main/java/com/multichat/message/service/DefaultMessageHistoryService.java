package com.multichat.message.service;

import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.message.dto.MessageCursorPage;
import com.multichat.message.dto.PersonalMessageReviewStatus;
import com.multichat.message.entity.ChatMessage;
import com.multichat.permission.PermissionService;
import com.multichat.websocket.RoomSubscriptionAuthorizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DefaultMessageHistoryService implements MessageHistoryService {
    private static final int MAX_PAGE_SIZE = 100;

    private final MessageMapper messageMapper;
    private final PermissionService permissionService;
    private final RoomSubscriptionAuthorizer subscriptionAuthorizer;

    public DefaultMessageHistoryService(MessageMapper messageMapper, PermissionService permissionService,
                                        RoomSubscriptionAuthorizer subscriptionAuthorizer) {
        this.messageMapper = messageMapper;
        this.permissionService = permissionService;
        this.subscriptionAuthorizer = subscriptionAuthorizer;
    }

    @Override
    @Transactional(readOnly = true)
    public MessageCursorPage publishedRoomHistory(UUID roomId, Long beforeSeq, int limit) {
        validateCursor(roomId, beforeSeq, limit);
        subscriptionAuthorizer.requireSubscribable(permissionService.currentActorId(), roomId);
        return page(messageMapper.findPublishedChatHistoryBefore(roomId, beforeSeq, limit + 1), limit);
    }

    @Override
    @Transactional(readOnly = true)
    public MessageCursorPage personalRoomHistory(UUID roomId, Long beforeSeq, int limit) {
        validateCursor(roomId, beforeSeq, limit);
        return page(messageMapper.findOwnChatHistoryBefore(permissionService.currentActorId(), roomId, beforeSeq,
                limit + 1), limit);
    }

    @Override
    @Transactional(readOnly = true)
    public PersonalMessageReviewStatus personalReviewStatus(UUID messageId) {
        if (messageId == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        ChatMessage message = messageMapper.findOwnChatById(permissionService.currentActorId(), messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        return new PersonalMessageReviewStatus(message.id(), message.roomId(), message.roomSeq(), message.status(),
                message.reviewDeadlineAt(), message.reviewedAt(), message.publishedAt());
    }

    private void validateCursor(UUID roomId, Long beforeSeq, int limit) {
        if (roomId == null || limit < 1 || limit > MAX_PAGE_SIZE || (beforeSeq != null && beforeSeq < 1)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private MessageCursorPage page(List<ChatMessage> fetched, int limit) {
        boolean hasMore = fetched.size() > limit;
        List<ChatMessage> items = hasMore ? fetched.subList(0, limit) : fetched;
        Long nextBeforeSeq = hasMore ? items.get(items.size() - 1).roomSeq() : null;
        return new MessageCursorPage(items, nextBeforeSeq, hasMore);
    }
}
