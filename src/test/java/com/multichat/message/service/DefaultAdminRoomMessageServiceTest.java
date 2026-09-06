package com.multichat.message.service;

import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.message.dto.AdminRoomMessageRequest;
import com.multichat.message.entity.ChatMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DefaultAdminRoomMessageServiceTest {
    private final AdminRoomMessageTransactionProcessor processor = mock(AdminRoomMessageTransactionProcessor.class);
    private final RoomPublishService publisher = mock(RoomPublishService.class);
    private final EmergencyNotificationCompensationService emergencyPublisher =
            mock(EmergencyNotificationCompensationService.class);
    private final MessageMapper messageMapper = mock(MessageMapper.class);

    @Test
    void broadcastCommitsAuthorizedRoomAndReportsUnauthorizedRoomWithoutAborting() {
        UUID allowedRoom = UUID.randomUUID();
        UUID deniedRoom = UUID.randomUUID();
        ChatMessage message = adminMessage(allowedRoom, "APPROVED", 17L);
        when(processor.createAdminMessage(eq(allowedRoom), eq("notice"), any())).thenReturn(message);
        when(processor.createAdminMessage(eq(deniedRoom), eq("notice"), any()))
                .thenThrow(new BusinessException(ErrorCode.ROOM_ACCESS_DENIED));
        when(publisher.publishAvailable(allowedRoom, message.id()))
                .thenReturn(new RoomPublishService.RoomPublicationAttempt(false, false));
        when(messageMapper.findById(message.id())).thenReturn(Optional.of(message));

        var result = service().broadcast(new AdminRoomMessageRequest(List.of(allowedRoom, deniedRoom), "notice"));

        assertEquals(2, result.results().size());
        assertEquals("SUCCESS", result.results().get(0).deliveryStatus());
        assertEquals(message.id(), result.results().get(0).messageId());
        assertEquals(17L, result.results().get(0).roomSeq());
        assertEquals("NO_PERMISSION", result.results().get(1).deliveryStatus());
        assertEquals("ROOM_ACCESS_DENIED", result.results().get(1).errorCode());
        verify(processor).createAdminMessage(eq(deniedRoom), eq("notice"), any());
    }

    @Test
    void emergencyNotificationKeepsPublishedRowForCompensationWhenInitialPushFails() {
        UUID roomId = UUID.randomUUID();
        ChatMessage notification = emergency(roomId, 9L);
        when(processor.createEmergencyNotification(eq(roomId), eq("urgent"), any())).thenReturn(notification);
        when(emergencyPublisher.deliver(notification.id())).thenReturn(false);
        when(messageMapper.findById(notification.id())).thenReturn(Optional.of(notification));

        var result = service().publishEmergencyNotification(new AdminRoomMessageRequest(List.of(roomId), "urgent"));

        var item = result.results().get(0);
        assertEquals("PENDING_COMPENSATION", item.deliveryStatus());
        assertEquals("PUBLISHED", item.messageStatus());
        assertEquals(9L, item.notificationSeq());
        assertEquals(null, item.roomSeq());
    }

    @Test
    void duplicateRoomIdsAreCollapsedBeforeWriting() {
        UUID roomId = UUID.randomUUID();
        ChatMessage message = adminMessage(roomId, "APPROVED", 1L);
        when(processor.createAdminMessage(eq(roomId), eq("notice"), any())).thenReturn(message);
        when(publisher.publishAvailable(roomId, message.id()))
                .thenReturn(new RoomPublishService.RoomPublicationAttempt(false, false));
        when(messageMapper.findById(message.id())).thenReturn(Optional.of(message));

        var result = service().broadcast(new AdminRoomMessageRequest(List.of(roomId, roomId), "notice"));

        assertEquals(1, result.results().size());
        verify(processor, times(1)).createAdminMessage(eq(roomId), eq("notice"), any());
    }

    private DefaultAdminRoomMessageService service() {
        return new DefaultAdminRoomMessageService(processor, new MessageContentValidator(), publisher,
                emergencyPublisher, messageMapper);
    }

    private ChatMessage adminMessage(UUID roomId, String status, long sequence) {
        Instant now = Instant.parse("2026-09-06T10:00:00Z");
        return new ChatMessage(UUID.randomUUID(), UUID.randomUUID(), roomId, UUID.randomUUID(), sequence, null,
                "ADMIN_MESSAGE", "notice", status, null, null, null, 0, now);
    }

    private ChatMessage emergency(UUID roomId, long sequence) {
        Instant now = Instant.parse("2026-09-06T10:00:00Z");
        return new ChatMessage(UUID.randomUUID(), UUID.randomUUID(), roomId, UUID.randomUUID(), null, sequence,
                "SYSTEM_NOTIFICATION", "urgent", "PUBLISHED", null, null, now, 0, now);
    }
}
