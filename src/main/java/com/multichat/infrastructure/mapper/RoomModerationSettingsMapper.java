package com.multichat.infrastructure.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface RoomModerationSettingsMapper {
    @Select("SELECT enabled FROM room_moderation_settings WHERE room_id = #{roomId}")
    Optional<Boolean> find(UUID roomId);

    @Insert("""
            INSERT INTO room_moderation_settings (room_id, enabled, updated_by, updated_at)
            VALUES (#{roomId}, #{enabled}, #{actorId}, #{updatedAt})
            ON CONFLICT (room_id) DO UPDATE SET
                enabled = EXCLUDED.enabled,
                updated_by = EXCLUDED.updated_by,
                updated_at = EXCLUDED.updated_at
            """)
    int upsert(@Param("roomId") UUID roomId, @Param("enabled") boolean enabled,
               @Param("actorId") UUID actorId, @Param("updatedAt") Instant updatedAt);
}
