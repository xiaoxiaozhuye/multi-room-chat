package com.multichat.permission.service;

import com.multichat.permission.dto.RoomAuthorizationGrantResponse;
import com.multichat.permission.dto.RoomAuthorizationResponse;
import com.multichat.permission.dto.RoomAuthorizationRevokeResponse;

import java.util.List;
import java.util.UUID;

/** System-admin-only management of room-scoped ROOM_ADMIN grants. */
public interface RoomAuthorizationService {
    List<RoomAuthorizationResponse> list(UUID roomId);

    RoomAuthorizationGrantResponse grant(UUID roomId, UUID adminUserId);

    RoomAuthorizationRevokeResponse revoke(UUID roomId, UUID adminUserId);
}
