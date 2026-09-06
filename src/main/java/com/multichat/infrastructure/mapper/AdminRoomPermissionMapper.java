package com.multichat.infrastructure.mapper;

import com.multichat.permission.entity.RoomAdminAuthorization;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface AdminRoomPermissionMapper {
    @Select("""
            SELECT EXISTS(SELECT 1 FROM admin_room_permissions
                          WHERE admin_id = #{adminId} AND room_id = #{roomId} AND revoked_at IS NULL)
            """)
    boolean hasActivePermission(@Param("adminId") UUID adminId, @Param("roomId") UUID roomId);

    @Select("""
            SELECT id, admin_id, room_id, granted_by, granted_at, revoked_at, revoked_by, created_at
            FROM admin_room_permissions
            WHERE admin_id = #{adminId} AND room_id = #{roomId} AND revoked_at IS NULL
            """)
    Optional<RoomAdminAuthorization> findActive(@Param("adminId") UUID adminId, @Param("roomId") UUID roomId);

    @Select("""
            SELECT id, admin_id, room_id, granted_by, granted_at, revoked_at, revoked_by, created_at
            FROM admin_room_permissions
            WHERE admin_id = #{adminId} AND room_id = #{roomId}
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    Optional<RoomAdminAuthorization> findLatest(@Param("adminId") UUID adminId, @Param("roomId") UUID roomId);

    @Select("""
            SELECT id, admin_id, room_id, granted_by, granted_at, revoked_at, revoked_by, created_at
            FROM admin_room_permissions
            WHERE room_id = #{roomId} AND revoked_at IS NULL
            ORDER BY created_at DESC, id DESC
            """)
    List<RoomAdminAuthorization> findActiveByRoomId(@Param("roomId") UUID roomId);

    @Insert("""
            INSERT INTO admin_room_permissions (id, admin_id, room_id, granted_by, granted_at, created_at, updated_at)
            VALUES (#{id}, #{adminId}, #{roomId}, #{grantedBy}, #{grantedAt}, #{createdAt}, #{createdAt})
            """)
    int insert(RoomAdminAuthorization authorization);

    @Update("""
            UPDATE admin_room_permissions
            SET revoked_at = #{revokedAt}, revoked_by = #{revokedBy}
            WHERE id = #{authorizationId} AND revoked_at IS NULL
            """)
    int revoke(@Param("authorizationId") UUID authorizationId, @Param("revokedBy") UUID revokedBy,
               @Param("revokedAt") Instant revokedAt);
}
