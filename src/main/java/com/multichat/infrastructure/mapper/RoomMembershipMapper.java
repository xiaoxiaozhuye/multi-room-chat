package com.multichat.infrastructure.mapper;

import com.multichat.member.entity.RoomMembership;
import com.multichat.member.dto.MyRoomMembershipRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
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

    @Select("""
            <script>
            SELECT m.id AS membership_id, m.user_id, m.room_id, m.status::text AS member_status,
                   m.created_at, m.activated_at AS joined_at,
                   r.id AS summary_room_id, r.name AS room_name, r.description AS room_description
            FROM user_chat_rooms m
            LEFT JOIN chat_rooms r ON r.id = m.room_id AND r.deleted_at IS NULL
            WHERE m.user_id = #{userId}
            <if test="memberStatus != null">
              AND m.status = CAST(#{memberStatus} AS membership_status)
            </if>
            ORDER BY m.created_at DESC, m.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<MyRoomMembershipRow> findByUser(@Param("userId") UUID userId,
                                         @Param("memberStatus") String memberStatus,
                                         @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            <script>
            SELECT id, user_id, room_id, status::text AS status, requested_at, activated_at, version
            FROM user_chat_rooms WHERE room_id = #{roomId}
            <if test="memberStatus != null"> AND status = CAST(#{memberStatus} AS membership_status) </if>
            ORDER BY activated_at DESC NULLS LAST, id DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<RoomMembership> findByRoom(@Param("roomId") UUID roomId, @Param("memberStatus") String memberStatus,
                                    @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            <script>
            SELECT m.id, m.user_id, m.room_id, m.status::text AS status, m.requested_at, m.activated_at, m.version
            FROM user_chat_rooms m WHERE 1 = 1
            <if test="roomId != null"> AND m.room_id = #{roomId} </if>
            <if test="userId != null"> AND m.user_id = #{userId} </if>
            <if test="memberStatus != null"> AND m.status = CAST(#{memberStatus} AS membership_status) </if>
            <if test="authorizedAdminId != null"> AND EXISTS (SELECT 1 FROM admin_room_permissions p WHERE p.admin_id = #{authorizedAdminId} AND p.room_id = m.room_id AND p.revoked_at IS NULL) </if>
            ORDER BY m.created_at ASC, m.id ASC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<RoomMembership> findJoinRequests(@Param("roomId") UUID roomId, @Param("userId") UUID userId,
                                           @Param("memberStatus") String memberStatus,
                                           @Param("authorizedAdminId") UUID authorizedAdminId,
                                           @Param("limit") int limit, @Param("offset") int offset);

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
