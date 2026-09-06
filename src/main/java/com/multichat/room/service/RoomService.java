package com.multichat.room.service;

import com.multichat.room.dto.CreateRoomRequest;
import com.multichat.room.dto.RoomDeletionResponse;
import com.multichat.room.dto.RoomPage;
import com.multichat.room.dto.UpdateRoomRequest;
import com.multichat.room.entity.ChatRoom;

import java.util.UUID;

public interface RoomService {
    ChatRoom create(UUID actorId, CreateRoomRequest request);
    RoomPage<ChatRoom> list(String name, String roomStatus, String joinMode, int page, int size);
    RoomPage<ChatRoom> listAdminVisible(String name, String roomStatus, String joinMode, int page, int size);
    ChatRoom get(UUID roomId);
    ChatRoom update(UUID roomId, UpdateRoomRequest request);
    RoomDeletionResponse delete(UUID roomId);
}
