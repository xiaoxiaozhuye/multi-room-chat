package com.multichat.infrastructure.metrics;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/** Explicit pool-usage meters in addition to Micrometer's Hikari meters. */
@Component
public class DatabasePoolMetrics {
    public DatabasePoolMetrics(MeterRegistry meterRegistry, DataSource dataSource) {
        if (!(dataSource instanceof HikariDataSource hikari)) return;
        Gauge.builder("chat.database.pool.active", hikari, DatabasePoolMetrics::active)
                .description("Active database connections in the Hikari pool")
                .register(meterRegistry);
        Gauge.builder("chat.database.pool.idle", hikari, DatabasePoolMetrics::idle)
                .description("Idle database connections in the Hikari pool")
                .register(meterRegistry);
        Gauge.builder("chat.database.pool.pending", hikari, DatabasePoolMetrics::pending)
                .description("Threads awaiting a database connection")
                .register(meterRegistry);
        Gauge.builder("chat.database.pool.max", hikari, source -> source.getMaximumPoolSize())
                .description("Configured maximum database connections")
                .register(meterRegistry);
        Gauge.builder("chat.database.pool.usage", hikari, DatabasePoolMetrics::usage)
                .description("Fraction of configured database connections that are active")
                .register(meterRegistry);
    }

    private static double active(HikariDataSource source) {
        HikariPoolMXBean pool = source.getHikariPoolMXBean();
        return pool == null ? 0D : pool.getActiveConnections();
    }

    private static double idle(HikariDataSource source) {
        HikariPoolMXBean pool = source.getHikariPoolMXBean();
        return pool == null ? 0D : pool.getIdleConnections();
    }

    private static double pending(HikariDataSource source) {
        HikariPoolMXBean pool = source.getHikariPoolMXBean();
        return pool == null ? 0D : pool.getThreadsAwaitingConnection();
    }

    private static double usage(HikariDataSource source) {
        int maximum = source.getMaximumPoolSize();
        return maximum <= 0 ? 0D : active(source) / maximum;
    }
}
