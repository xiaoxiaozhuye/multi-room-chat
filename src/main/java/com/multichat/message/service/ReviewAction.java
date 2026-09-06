package com.multichat.message.service;

import java.util.Locale;

public enum ReviewAction {
    APPROVE("APPROVED", "MESSAGE_APPROVE"),
    REJECT("REJECTED", "MESSAGE_REJECT");

    private final String targetStatus;
    private final String auditAction;

    ReviewAction(String targetStatus, String auditAction) {
        this.targetStatus = targetStatus;
        this.auditAction = auditAction;
    }

    public String targetStatus() { return targetStatus; }
    public String auditAction() { return auditAction; }

    public static ReviewAction parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
