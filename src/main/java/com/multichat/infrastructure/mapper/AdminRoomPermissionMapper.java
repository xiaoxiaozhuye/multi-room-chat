package com.multichat.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

@Mapper
public interface AdminRoomPermissionMapper {
    @Select("""
            SELECT EXISTS(SELECT 1 FROM admin_room_permissions
                          WHERE admin_id = #{adminId} AND room_id = #{roomId} AND revoked_at IS NULL)
            """)
    boolean hasActivePermission(UUID adminId, UUID roomId);
}
