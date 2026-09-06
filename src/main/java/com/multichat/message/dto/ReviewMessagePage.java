package com.multichat.message.dto;

import com.multichat.message.entity.ChatMessage;

import java.util.List;

public record ReviewMessagePage(List<ChatMessage> items, int page, int size, boolean hasNext) {
}
