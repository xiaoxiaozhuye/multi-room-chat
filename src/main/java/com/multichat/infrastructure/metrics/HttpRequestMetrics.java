package com.multichat.infrastructure.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.LongAdder;

/** Process-lifetime API error ratio, including both client and server errors. */
@Component
public class HttpRequestMetrics {
    private final LongAdder requests = new LongAdder();
    private final LongAdder errors = new LongAdder();

    public HttpRequestMetrics(MeterRegistry meterRegistry) {
        Gauge.builder("chat.http.requests", requests, LongAdder::sum)
                .description("HTTP requests completed by this application instance")
                .register(meterRegistry);
        Gauge.builder("chat.http.errors", errors, LongAdder::sum)
                .description("Completed HTTP requests with a 4xx or 5xx status")
                .register(meterRegistry);
        Gauge.builder("chat.http.error.rate", this, HttpRequestMetrics::errorRate)
                .description("Fraction of completed HTTP requests with a 4xx or 5xx status")
                .register(meterRegistry);
    }

    public void record(int status) {
        requests.increment();
        if (status >= 400) errors.increment();
    }

    private double errorRate() {
        long total = requests.sum();
        return total == 0 ? 0D : (double) errors.sum() / total;
    }
}
