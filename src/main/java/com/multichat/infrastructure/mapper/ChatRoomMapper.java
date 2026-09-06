package com.multichat.infrastructure.mapper;

import com.multichat.room.entity.ChatRoom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface ChatRoomMapper {
    @Select("""
            SELECT id, name, description, max_members, join_mode::text AS join_mode, status::text AS status,
                   created_by, version, created_at, updated_at
            FROM chat_rooms WHERE id = #{roomId} AND deleted_at IS NULL
            """)
    Optional<ChatRoom> findActiveById(UUID roomId);
}
