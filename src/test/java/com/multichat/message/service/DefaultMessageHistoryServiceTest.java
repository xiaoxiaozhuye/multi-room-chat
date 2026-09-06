package com.multichat.message.service;

import com.multichat.common.exception.BusinessException;
import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.message.dto.MessageCursorPage;
import com.multichat.message.dto.PersonalMessageReviewStatus;
import com.multichat.message.entity.ChatMessage;
import com.multichat.permission.PermissionService;
import com.multichat.websocket.RoomSubscriptionAuthorizer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DefaultMessageHistoryServiceTest {
    private final MessageMapper messageMapper = mock(MessageMapper.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final RoomSubscriptionAuthorizer subscriptionAuthorizer = mock(RoomSubscriptionAuthorizer.class);
    private final DefaultMessageHistoryService service = new DefaultMessageHistoryService(
            messageMapper, permissionService, subscriptionAuthorizer);
    private final UUID actorId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();

    @Test
    void publishedHistoryAuthorizesTheReaderAndUsesRoomSequenceKeyset() {
        ChatMessage latest = message(UUID.randomUUID(), actorId, 10, "PUBLISHED");
        ChatMessage older = message(UUID.randomUUID(), actorId, 9, "PUBLISHED");
        ChatMessage sentinel = message(UUID.randomUUID(), actorId, 8, "PUBLISHED");
        when(permissionService.currentActorId()).thenReturn(actorId);
        when(messageMapper.findPublishedChatHistoryBefore(roomId, 11L, 3))
                .thenReturn(List.of(latest, older, sentinel));

        MessageCursorPage page = service.publishedRoomHistory(roomId, 11L, 2);

        verify(subscriptionAuthorizer).requireSubscribable(actorId, roomId);
        verify(messageMapper).findPublishedChatHistoryBefore(roomId, 11L, 3);
        assertEquals(List.of(latest, older), page.items());
        assertEquals(9L, page.nextBeforeSeq());
        assertEquals(true, page.hasMore());
    }

    @Test
    void personalHistoryOnlyQueriesTheAuthenticatedSenderAndKeepsTerminalReviewStates() {
        ChatMessage rejected = message(UUID.randomUUID(), actorId, 7, "REJECTED");
        ChatMessage timeout = message(UUID.randomUUID(), actorId, 6, "TIMEOUT");
        when(permissionService.currentActorId()).thenReturn(actorId);
        when(messageMapper.findOwnChatHistoryBefore(actorId, roomId, null, 51))
                .thenReturn(List.of(rejected, timeout));

        MessageCursorPage page = service.personalRoomHistory(roomId, null, 50);

        verify(messageMapper).findOwnChatHistoryBefore(actorId, roomId, null, 51);
        verifyNoInteractions(subscriptionAuthorizer);
        assertEquals(List.of(rejected, timeout), page.items());
        assertEquals(null, page.nextBeforeSeq());
        assertEquals(false, page.hasMore());
    }

    @Test
    void anotherUsersMessageIsIndistinguishableFromMissingForReviewStatus() {
        UUID messageId = UUID.randomUUID();
        when(permissionService.currentActorId()).thenReturn(actorId);
        when(messageMapper.findOwnChatById(actorId, messageId)).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class, () -> service.personalReviewStatus(messageId));

        assertEquals("MESSAGE_NOT_FOUND", error.code());
        verify(messageMapper).findOwnChatById(actorId, messageId);
    }

    @Test
    void personalReviewStatusReturnsOnlyTheCallersLifecycleFields() {
        UUID messageId = UUID.randomUUID();
        ChatMessage pending = message(messageId, actorId, 7, "PENDING_REVIEW");
        when(permissionService.currentActorId()).thenReturn(actorId);
        when(messageMapper.findOwnChatById(actorId, messageId)).thenReturn(Optional.of(pending));

        PersonalMessageReviewStatus status = service.personalReviewStatus(messageId);

        assertEquals(messageId, status.messageId());
        assertEquals(roomId, status.roomId());
        assertEquals("PENDING_REVIEW", status.messageStatus());
    }

    @Test
    void rejectsNonPositiveBeforeSequenceWithoutHittingStorage() {
        assertThrows(BusinessException.class, () -> service.personalRoomHistory(roomId, 0L, 20));
        verify(messageMapper, never()).findOwnChatHistoryBefore(any(), any(), any(), anyInt());
    }

    private ChatMessage message(UUID id, UUID senderId, long sequence, String status) {
        Instant now = Instant.parse("2026-09-06T10:00:00Z");
        return new ChatMessage(id, UUID.randomUUID(), roomId, senderId, sequence, null, "CHAT", "body", status,
                now.plusSeconds(30), null, "PUBLISHED".equals(status) ? now : null, 0, now);
    }
}
