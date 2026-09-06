package com.multichat.infrastructure.mapper;

import com.multichat.room.entity.ChatRoom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
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

    /**
     * Serializes membership capacity changes for a room.  Callers must keep the
     * surrounding PostgreSQL transaction open until their membership write has
     * completed.
     */
    @Select("""
            SELECT id, name, description, max_members, join_mode::text AS join_mode, status::text AS status,
                   created_by, version, created_at, updated_at
            FROM chat_rooms WHERE id = #{roomId} AND deleted_at IS NULL
            FOR UPDATE
            """)
    Optional<ChatRoom> findActiveByIdForUpdate(@Param("roomId") UUID roomId);

    @Select("SELECT EXISTS(SELECT 1 FROM chat_rooms WHERE id = #{roomId} AND deleted_at IS NOT NULL)")
    boolean isDeleted(UUID roomId);

    @Select("""
            <script>
            SELECT id, name, description, max_members, join_mode::text AS join_mode, status::text AS status,
                   created_by, version, created_at, updated_at
            FROM chat_rooms
            WHERE deleted_at IS NULL
            <if test="name != null and name != ''">
              AND lower(name) LIKE lower(#{name}) || '%'
            </if>
            <if test="status != null">
              AND status = CAST(#{status} AS room_status)
            </if>
            <if test="joinMode != null">
              AND join_mode = CAST(#{joinMode} AS room_join_mode)
            </if>
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ChatRoom> findPage(@Param("name") String name, @Param("status") String status,
                            @Param("joinMode") String joinMode, @Param("limit") int limit,
                            @Param("offset") int offset);

    /** Management listings for ROOM_ADMIN must never expose ungranted rooms. */
    @Select("""
            <script>
            SELECT r.id, r.name, r.description, r.max_members, r.join_mode::text AS join_mode, r.status::text AS status,
                   r.created_by, r.version, r.created_at, r.updated_at
            FROM chat_rooms r JOIN admin_room_permissions p ON p.room_id = r.id
            WHERE p.admin_id = #{adminId} AND p.revoked_at IS NULL AND r.deleted_at IS NULL
            <if test="name != null and name != ''">
              AND lower(r.name) LIKE lower(#{name}) || '%'
            </if>
            <if test="status != null">
              AND r.status = CAST(#{status} AS room_status)
            </if>
            <if test="joinMode != null">
              AND r.join_mode = CAST(#{joinMode} AS room_join_mode)
            </if>
            ORDER BY r.created_at DESC, r.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ChatRoom> findPageGrantedToAdmin(@Param("adminId") UUID adminId, @Param("name") String name,
                                          @Param("status") String status, @Param("joinMode") String joinMode,
                                          @Param("limit") int limit, @Param("offset") int offset);

    @Insert("""
            INSERT INTO chat_rooms (id, name, description, max_members, join_mode, status, created_by,
                                    version, created_at, updated_at)
            VALUES (#{id}, #{name}, #{description}, #{maxMembers}, CAST(#{joinMode} AS room_join_mode),
                    CAST(#{status} AS room_status), #{createdBy}, #{version}, #{createdAt}, #{updatedAt})
            """)
    int insert(ChatRoom room);

    @Update("""
            UPDATE chat_rooms
            SET name = #{name}, description = #{description}, max_members = #{maxMembers},
                join_mode = CAST(#{joinMode} AS room_join_mode), status = CAST(#{status} AS room_status),
                version = version + 1, updated_at = #{updatedAt}
            WHERE id = #{id} AND deleted_at IS NULL AND version = #{version}
            """)
    int update(ChatRoom room);

    @Update("""
            UPDATE chat_rooms
            SET deleted_at = #{deletedAt}, deleted_by = #{deletedBy}, version = version + 1, updated_at = #{deletedAt}
            WHERE id = #{roomId} AND deleted_at IS NULL
            """)
    int logicalDelete(@Param("roomId") UUID roomId, @Param("deletedBy") UUID deletedBy,
                      @Param("deletedAt") Instant deletedAt);
}
