package com.multichat.infrastructure.metrics;

import com.multichat.common.api.ApiResponse;
import com.multichat.permission.PermissionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Read-only, business-safe metrics for the system-administration UI. */
@RestController
@RequestMapping("/api/v1/admin/system/metrics")
public class SystemMetricsController {
    private final MeterRegistry meterRegistry;
    private final PermissionService permissionService;

    public SystemMetricsController(MeterRegistry meterRegistry, PermissionService permissionService) {
        this.meterRegistry = meterRegistry;
        this.permissionService = permissionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SystemMetrics>> query(
            @RequestParam(defaultValue = "CURRENT") MetricsWindow window) {
        permissionService.requireSystemAdmin();

        // The counters are process-lifetime meters at present; the other fields
        // are already calculated for the current UTC day by their metric source.
        // Keep the accepted window explicit so clients receive validation for an
        // unsupported value and can continue to use the documented contract.
        return ResponseEntity.ok(ApiResponse.success(snapshot(window)));
    }

    private SystemMetrics snapshot(MetricsWindow window) {
        return new SystemMetrics(
                Instant.now(),
                wholeGauge("chat.websocket.connections"),
                wholeGauge("chat.review.pending"),
                wholeCounter("chat.review.timeouts"),
                wholeGauge("chat.messages.today"),
                Math.round(gauge("chat.review.average.duration") * 1_000D),
                wholeCounter("chat.push.failures"),
                gauge("chat.http.error.rate"),
                new DatabasePool(
                        wholeGauge("chat.database.pool.active"),
                        wholeGauge("chat.database.pool.idle"),
                        wholeGauge("chat.database.pool.max")));
    }

    private long wholeGauge(String name) {
        return Math.round(gauge(name));
    }

    private long wholeCounter(String name) {
        Counter counter = meterRegistry.find(name).counter();
        return counter == null ? 0L : Math.round(counter.count());
    }

    private double gauge(String name) {
        Gauge gauge = meterRegistry.find(name).gauge();
        if (gauge == null) return 0D;
        double value = gauge.value();
        return Double.isFinite(value) ? value : 0D;
    }

    public enum MetricsWindow { CURRENT, TODAY }

    public record SystemMetrics(Instant observedAt, long webSocketConnectionCount, long pendingReviewCount,
                                long reviewTimeoutCountToday, long messageCountToday,
                                long averageReviewLatencyMsToday, long pushFailureCountToday,
                                double httpErrorRate, DatabasePool databasePool) { }

    public record DatabasePool(long active, long idle, long max) { }
}
