package com.multichat.message.service;

import com.multichat.message.dto.BatchReviewRequest;
import com.multichat.message.dto.BatchReviewResponse;
import com.multichat.message.dto.ReviewMessagePage;
import com.multichat.message.dto.ReviewMessageQuery;
import com.multichat.message.entity.ChatMessage;

import java.util.UUID;

public interface MessageReviewService {
    ReviewMessagePage query(ReviewMessageQuery query);

    ChatMessage approve(UUID messageId);

    ChatMessage reject(UUID messageId);

    BatchReviewResponse batch(BatchReviewRequest request);
}
