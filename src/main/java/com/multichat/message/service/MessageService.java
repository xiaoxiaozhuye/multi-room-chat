package com.multichat.message.service;

import com.multichat.message.dto.SubmitMessageRequest;
import com.multichat.message.entity.ChatMessage;

import java.util.UUID;

public interface MessageService {
    ChatMessage submit(UUID senderId, UUID requestId, SubmitMessageRequest request);
}
