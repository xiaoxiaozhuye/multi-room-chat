package com.multichat.infrastructure.mapper;

import com.multichat.member.entity.RoomMembership;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface RoomMembershipMapper {
    @Select("""
            SELECT id, user_id, room_id, status::text AS status, requested_at, activated_at, version
            FROM user_chat_rooms WHERE user_id = #{userId} AND room_id = #{roomId}
              AND status = 'ACTIVE'
            """)
    Optional<RoomMembership> findActive(UUID userId, UUID roomId);
}
