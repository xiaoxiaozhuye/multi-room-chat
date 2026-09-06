package com.multichat.common.api;

/** A safe, field-level reason that can be shown beside a client input. */
public record ValidationError(String field, String reason) {
}
