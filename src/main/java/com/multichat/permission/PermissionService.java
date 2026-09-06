package com.multichat.permission;

import java.util.UUID;

/**
 * Server-side authorization boundary for administration endpoints and domain
 * commands. Actor identity is always resolved from the authenticated request,
 * never from command payloads.
 */
public interface PermissionService {
    UUID currentActorId();

    void requireSystemAdmin();

    void requireRoomPermission(UUID roomId, AdminOperation operation);

    boolean hasRoomPermission(UUID roomId, AdminOperation operation);
}
