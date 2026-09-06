package com.multichat.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "chat.review")
public record ReviewProperties(Duration timeout, Duration scanInterval) {
}
