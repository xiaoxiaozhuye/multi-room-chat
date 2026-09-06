package com.multichat.message.service;

import com.multichat.message.dto.MessageCursorPage;
import com.multichat.message.dto.PersonalMessageReviewStatus;

import java.util.UUID;

/** Read model for published room history and a user's own review history. */
public interface MessageHistoryService {
    MessageCursorPage publishedRoomHistory(UUID roomId, Long beforeSeq, int limit);

    MessageCursorPage personalRoomHistory(UUID roomId, Long beforeSeq, int limit);

    PersonalMessageReviewStatus personalReviewStatus(UUID messageId);
}
