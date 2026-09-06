package com.multichat.permission;

/**
 * Administrative capabilities guarded by the same room-scope authorization
 * rule. The enum makes call sites explicit while keeping the role decision in
 * one service.
 */
public enum AdminOperation {
    ROOM_MANAGEMENT,
    JOIN_APPROVAL,
    MESSAGE_REVIEW,
    ADMIN_BROADCAST,
    EMERGENCY_NOTIFICATION,
    ROOM_AUDIT_QUERY
}
