package com.multichat.infrastructure.metrics;

import com.multichat.websocket.WebSocketSessionRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class WebSocketMetrics {
    public WebSocketMetrics(MeterRegistry meterRegistry, WebSocketSessionRegistry sessionRegistry) {
        meterRegistry.gauge("chat.websocket.connections", sessionRegistry, WebSocketSessionRegistry::activeConnectionCount);
    }
}
