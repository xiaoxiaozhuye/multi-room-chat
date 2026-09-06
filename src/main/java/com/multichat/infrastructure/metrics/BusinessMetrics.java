package com.multichat.infrastructure.metrics;

import com.multichat.infrastructure.mapper.MessageMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.function.DoubleSupplier;

/**
 * Business-facing meters. Database-backed gauges deliberately use PostgreSQL as
 * the source of truth, rather than a node-local cache, so their values remain
 * meaningful in a multi-node deployment.
 */
@Component
public class BusinessMetrics {
    private static final Logger log = LoggerFactory.getLogger(BusinessMetrics.class);
    private final Counter reviewTimeouts;
    private final Counter pushFailures;
    private final DistributionSummary reviewDurationSeconds;

    public BusinessMetrics(MeterRegistry meterRegistry, MessageMapper messageMapper) {
        reviewTimeouts = Counter.builder("chat.review.timeouts")
                .description("Number of messages that timed out while awaiting review")
                .register(meterRegistry);
        pushFailures = Counter.builder("chat.push.failures")
                .description("Number of unsuccessful WebSocket message delivery attempts")
                .register(meterRegistry);
        reviewDurationSeconds = DistributionSummary.builder("chat.review.duration")
                .baseUnit("seconds")
                .description("Time from message submission to a terminal review decision")
                .register(meterRegistry);

        Gauge.builder("chat.review.pending", messageMapper,
                        mapper -> safely(() -> mapper.countPendingReviews()))
                .description("Messages currently awaiting review")
                .register(meterRegistry);
        Gauge.builder("chat.messages.today", messageMapper,
                        mapper -> safely(() -> mapper.countMessagesCreatedTodayUtc()))
                .description("Messages submitted since the start of the current UTC day")
                .register(meterRegistry);
        Gauge.builder("chat.review.average.duration", messageMapper,
                        mapper -> safely(mapper::averageReviewDurationSeconds))
                .baseUnit("seconds")
                .description("Average submission-to-review duration for reviewed messages")
                .register(meterRegistry);
    }

    public void recordReviewCompleted(Instant createdAt, Instant reviewedAt) {
        if (createdAt != null && reviewedAt != null && !reviewedAt.isBefore(createdAt)) {
            reviewDurationSeconds.record((reviewedAt.toEpochMilli() - createdAt.toEpochMilli()) / 1_000D);
        }
    }

    public void recordReviewTimeout() {
        reviewTimeouts.increment();
    }

    public void recordPushFailure() {
        pushFailures.increment();
    }

    private double safely(DoubleSupplier supplier) {
        try {
            return supplier.getAsDouble();
        } catch (RuntimeException exception) {
            // A monitoring scrape must not turn a transient database failure
            // into an actuator endpoint failure. NaN makes the missing sample
            // explicit to monitoring systems while the health indicator reports
            // the underlying datasource failure.
            log.warn("Unable to collect business metric from database", exception);
            return Double.NaN;
        }
    }
}
