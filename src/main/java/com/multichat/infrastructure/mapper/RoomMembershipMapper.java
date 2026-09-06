package com.multichat.infrastructure.mapper;

import com.multichat.member.entity.RoomMembership;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
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

    @Select("""
            SELECT id, user_id, room_id, status::text AS status, requested_at, activated_at, version
            FROM user_chat_rooms WHERE user_id = #{userId} AND room_id = #{roomId}
              AND status IN (CAST('PENDING' AS membership_status), CAST('ACTIVE' AS membership_status))
            ORDER BY created_at DESC LIMIT 1
            """)
    Optional<RoomMembership> findLive(@Param("userId") UUID userId, @Param("roomId") UUID roomId);

    @Select("""
            SELECT id, user_id, room_id, status::text AS status, requested_at, activated_at, version
            FROM user_chat_rooms WHERE id = #{membershipId}
            """)
    Optional<RoomMembership> findById(@Param("membershipId") UUID membershipId);

    @Select("""
            SELECT id, user_id, room_id, status::text AS status, requested_at, activated_at, version
            FROM user_chat_rooms WHERE id = #{membershipId}
            FOR UPDATE
            """)
    Optional<RoomMembership> findByIdForUpdate(@Param("membershipId") UUID membershipId);

    @Select("""
            SELECT count(*) FROM user_chat_rooms
            WHERE room_id = #{roomId} AND status = CAST('ACTIVE' AS membership_status)
            """)
    int countActiveByRoomId(@Param("roomId") UUID roomId);

    @Insert("""
            INSERT INTO user_chat_rooms (id, user_id, room_id, status, requested_at, activated_at,
                                        version, created_at, updated_at)
            VALUES (#{id}, #{userId}, #{roomId}, CAST(#{status} AS membership_status), #{requestedAt},
                    #{activatedAt}, #{version}, #{requestedAt}, #{requestedAt})
            """)
    int insert(RoomMembership membership);

    @Update("""
            UPDATE user_chat_rooms
            SET status = CAST('ACTIVE' AS membership_status), activated_at = #{resolvedAt},
                resolved_at = #{resolvedAt}, resolved_by = #{resolvedBy}, version = version + 1,
                updated_at = #{resolvedAt}
            WHERE id = #{membershipId} AND status = CAST('PENDING' AS membership_status)
            """)
    int approvePending(@Param("membershipId") UUID membershipId, @Param("resolvedBy") UUID resolvedBy,
                       @Param("resolvedAt") Instant resolvedAt);

    @Update("""
            UPDATE user_chat_rooms
            SET status = CAST('REJECTED' AS membership_status), resolved_at = #{resolvedAt},
                resolved_by = #{resolvedBy}, version = version + 1, updated_at = #{resolvedAt}
            WHERE id = #{membershipId} AND status = CAST('PENDING' AS membership_status)
            """)
    int rejectPending(@Param("membershipId") UUID membershipId, @Param("resolvedBy") UUID resolvedBy,
                      @Param("resolvedAt") Instant resolvedAt);

    @Update("""
            UPDATE user_chat_rooms
            SET status = CAST('EXITED' AS membership_status), exited_at = #{exitedAt},
                version = version + 1, updated_at = #{exitedAt}
            WHERE user_id = #{userId} AND room_id = #{roomId}
              AND status = CAST('ACTIVE' AS membership_status)
            """)
    int exitActive(@Param("roomId") UUID roomId, @Param("userId") UUID userId, @Param("exitedAt") Instant exitedAt);
}
