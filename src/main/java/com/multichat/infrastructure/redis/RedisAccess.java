package com.multichat.infrastructure.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Makes every Redis use explicitly best-effort.  Redis failures must never
 * prevent callers from consulting PostgreSQL for an authoritative decision.
 */
@Component
public class RedisAccess {
    private static final Logger log = LoggerFactory.getLogger(RedisAccess.class);

    private final RedisInfrastructureProperties properties;

    public RedisAccess(RedisInfrastructureProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public <T> RedisCallResult<T> read(String operation, Supplier<T> action, T fallback) {
        if (!isEnabled()) {
            return RedisCallResult.unavailable(fallback);
        }
        try {
            return RedisCallResult.available(action.get());
        } catch (RuntimeException exception) {
            log.warn("redis_operation_unavailable operation={} reason={}", operation,
                    exception.getClass().getSimpleName());
            return RedisCallResult.unavailable(fallback);
        }
    }

    public boolean write(String operation, Runnable action) {
        if (!isEnabled()) {
            return false;
        }
        try {
            action.run();
            return true;
        } catch (RuntimeException exception) {
            log.warn("redis_operation_unavailable operation={} reason={}", operation,
                    exception.getClass().getSimpleName());
            return false;
        }
    }
}
