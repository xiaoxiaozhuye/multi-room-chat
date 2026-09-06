package com.multichat.room.service;

import com.multichat.room.dto.CreateRoomRequest;
import com.multichat.room.entity.ChatRoom;

import java.util.UUID;

public interface RoomService {
    ChatRoom create(UUID actorId, CreateRoomRequest request);
}
