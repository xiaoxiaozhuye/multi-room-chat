package com.multichat.message.retention;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily default; each run uses a fixed cutoff and is safe to repeat after failures. */
@Component
public class MessageRetentionScheduler {
    private final MessageRetentionService retentionService;

    public MessageRetentionScheduler(MessageRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(cron = "${chat.retention.messages.cron:0 20 3 * * *}", zone = "UTC")
    public void run() {
        retentionService.runScheduled();
    }
}
