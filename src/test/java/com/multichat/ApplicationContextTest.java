package com.multichat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApplicationContextTest {
    @Test
    void applicationContextStarts() {
        // Context creation validates configuration, security, WebSocket and mapper wiring.
    }
}
