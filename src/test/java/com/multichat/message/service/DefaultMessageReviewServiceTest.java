package com.multichat.message.service;

import com.multichat.common.exception.BusinessException;
import com.multichat.infrastructure.mapper.MessageMapper;
import com.multichat.message.dto.BatchReviewRequest;
import com.multichat.message.dto.ReviewMessageQuery;
import com.multichat.message.entity.ChatMessage;
import com.multichat.permission.PermissionService;
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

class DefaultMessageReviewServiceTest {
    private final MessageMapper messageMapper = mock(MessageMapper.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final MessageReviewTransactionProcessor processor = mock(MessageReviewTransactionProcessor.class);

    @Test
    void roomAdminQueryIsBoundToTheirAuthorizedRoomSet() {
        UUID adminId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(permissionService.isSystemAdmin()).thenReturn(false);
        when(permissionService.isRoomAdmin()).thenReturn(true);
        when(permissionService.currentActorId()).thenReturn(adminId);
        when(messageMapper.findReviewMessages(eq(null), eq(null), eq("PENDING_REVIEW"), eq(null), eq(null),
                eq(adminId), eq(51), eq(0))).thenReturn(List.of());

        var page = service().query(new ReviewMessageQuery(null, null, "PENDING_REVIEW", null, null, 1, 50));

        assertEquals(0, page.items().size());
        verify(permissionService, never()).requireRoomPermission(any(), any());
    }

    @Test
    void batchDeduplicatesAndReportsEachBusinessFailureWithoutAborting() {
        UUID approvedId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        ChatMessage approved = message(approvedId, "APPROVED");
        when(processor.review(approvedId, ReviewAction.APPROVE)).thenReturn(approved);
        when(processor.review(missingId, ReviewAction.APPROVE))
                .thenThrow(new BusinessException(com.multichat.common.exception.ErrorCode.MESSAGE_NOT_FOUND));

        var response = service().batch(new BatchReviewRequest(List.of(approvedId, approvedId, missingId), "APPROVE"));

        assertEquals(2, response.results().size());
        assertEquals("APPROVED", response.results().get(0).reviewResult());
        assertEquals("NOT_FOUND", response.results().get(1).reviewResult());
        verify(processor, times(1)).review(approvedId, ReviewAction.APPROVE);
    }

    @Test
    void rejectsInvalidStatusAndBatchAction() {
        assertThrows(BusinessException.class,
                () -> service().query(new ReviewMessageQuery(null, null, "UNKNOWN", null, null, 1, 10)));
        assertThrows(BusinessException.class,
                () -> service().batch(new BatchReviewRequest(List.of(UUID.randomUUID()), "DELETE")));
    }

    private DefaultMessageReviewService service() {
        return new DefaultMessageReviewService(messageMapper, permissionService, processor);
    }

    private ChatMessage message(UUID id, String status) {
        Instant now = Instant.now();
        return new ChatMessage(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1L, null,
                "CHAT", "hello", status, now.plusSeconds(30), now, null, 1, now);
    }
}
