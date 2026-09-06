package com.multichat.message.service;

import com.multichat.infrastructure.mapper.EmergencyNotificationCompensationMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Retries durable emergency deliveries independently from the normal queue. */
@Component
public class EmergencyNotificationCompensationScheduler {
    private final EmergencyNotificationCompensationMapper compensationMapper;
    private final EmergencyNotificationCompensationService compensationService;

    public EmergencyNotificationCompensationScheduler(EmergencyNotificationCompensationMapper compensationMapper,
                                                       EmergencyNotificationCompensationService compensationService) {
        this.compensationMapper = compensationMapper;
        this.compensationService = compensationService;
    }

    @Scheduled(fixedDelayString = "${chat.emergency.compensation-interval:PT5S}")
    @Transactional
    public void compensate() {
        compensationMapper.lockNextMessageIds(100).forEach(compensationService::deliver);
    }
}
