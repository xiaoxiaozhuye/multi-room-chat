package com.multichat.infrastructure.redis;

/** UNKNOWN is intentional: absence of Redis must not be interpreted as offline. */
public enum OnlineStatus {
    ONLINE,
    OFFLINE,
    UNKNOWN
}
