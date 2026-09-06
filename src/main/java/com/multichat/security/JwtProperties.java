package com.multichat.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "chat.security.jwt")
public record JwtProperties(String issuer, String secret, Duration accessTokenTtl) {
}
