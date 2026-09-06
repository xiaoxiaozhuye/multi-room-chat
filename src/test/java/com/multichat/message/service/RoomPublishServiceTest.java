package com.multichat.message.service;

import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.message.entity.ChatMessage;
import com.multichat.websocket.RoomMessageNotifier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RoomPublishServiceTest {
    private final MessageMapper messageMapper = mock(MessageMapper.class);
    private final RoomMessageNotifier notifier = mock(RoomMessageNotifier.class);
    private final UUID roomId = UUID.randomUUID();

    @Test
    void laterApprovedMessageWaitsForEarlierPendingReview() {
        when(messageMapper.lockRoomForPublication(roomId)).thenReturn(Optional.of(roomId));
        when(messageMapper.findNextRoomPublishCandidateForUpdate(roomId)).thenReturn(Optional.of(message(1, "PENDING_REVIEW")));

        service().publishAvailable(roomId);

        verifyNoInteractions(notifier);
        verify(messageMapper, never()).markApprovedPublished(any(), any());
        verify(messageMapper, never()).advanceRoomPublishCursor(any(), anyLong());
    }

    @Test
    void rejectedPredecessorIsSkippedBeforeApprovedSuccessorIsPublishedAndDelivered() {
        ChatMessage rejected = message(1, "REJECTED");
        ChatMessage approved = message(2, "APPROVED");
        when(messageMapper.lockRoomForPublication(roomId)).thenReturn(Optional.of(roomId));
        when(messageMapper.findNextRoomPublishCandidateForUpdate(roomId))
                .thenReturn(Optional.of(rejected), Optional.of(approved), Optional.empty());
        when(messageMapper.advanceRoomPublishCursor(roomId, 1)).thenReturn(1);
        when(messageMapper.advanceRoomPublishCursor(roomId, 2)).thenReturn(1);
        when(messageMapper.markApprovedPublished(eq(approved.id()), any())).thenReturn(1);

        service().publishAvailable(roomId);

        var ordered = inOrder(messageMapper, notifier);
        ordered.verify(messageMapper).advanceRoomPublishCursor(roomId, 1);
        ordered.verify(messageMapper).markApprovedPublished(eq(approved.id()), any());
        ordered.verify(messageMapper).advanceRoomPublishCursor(roomId, 2);
        ordered.verify(notifier).notifyPublished(argThat(message ->
                approved.id().equals(message.id()) && "PUBLISHED".equals(message.status())));
    }

    @Test
    void publicationIsCommittedEvenWhenThereAreNoActiveSubscribers() {
        ChatMessage approved = message(1, "APPROVED");
        when(messageMapper.lockRoomForPublication(roomId)).thenReturn(Optional.of(roomId));
        when(messageMapper.findNextRoomPublishCandidateForUpdate(roomId))
                .thenReturn(Optional.of(approved), Optional.empty());
        when(messageMapper.markApprovedPublished(eq(approved.id()), any())).thenReturn(1);
        when(messageMapper.advanceRoomPublishCursor(roomId, 1)).thenReturn(1);

        service().publishAvailable(roomId);

        verify(messageMapper).markApprovedPublished(eq(approved.id()), any());
        verify(messageMapper).advanceRoomPublishCursor(roomId, 1);
        verify(notifier).notifyPublished(any());
    }

    private RoomPublishService service() {
        return new RoomPublishService(messageMapper, notifier);
    }

    private ChatMessage message(long roomSeq, String status) {
        Instant now = Instant.parse("2026-09-06T10:00:00Z");
        return new ChatMessage(UUID.randomUUID(), UUID.randomUUID(), roomId, UUID.randomUUID(), roomSeq, null,
                "CHAT", "hello", status, now.plusSeconds(30), now, null, 1, now);
    }
}
