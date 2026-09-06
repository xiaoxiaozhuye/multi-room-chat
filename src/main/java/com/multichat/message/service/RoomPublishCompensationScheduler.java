package com.multichat.message.service;

import com.multichat.infrastructure.mapper.MessageMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retries APPROVED messages whose first WebSocket delivery did not complete. */
@Component
public class RoomPublishCompensationScheduler {
    private final MessageMapper messageMapper;
    private final RoomPublishService roomPublishService;

    public RoomPublishCompensationScheduler(MessageMapper messageMapper, RoomPublishService roomPublishService) {
        this.messageMapper = messageMapper;
        this.roomPublishService = roomPublishService;
    }

    @Scheduled(fixedDelayString = "${chat.publish.compensation-interval:PT5S}")
    public void compensate() {
        int limit = 100;
        messageMapper.findRoomsWithApprovedMessages(limit).forEach(roomPublishService::compensateAvailable);
    }
}
