package com.multichat.infrastructure.mapper;

import com.multichat.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper
public interface AuditLogMapper {
    @Insert("""
            INSERT INTO audit_logs (id, request_id, actor_id, action, resource_type, resource_id, room_id, message_id,
                                    before_state, after_state, detail, created_at)
            VALUES (#{id}, #{requestId}, #{actorId}, CAST(#{action} AS audit_action),
                    CAST(#{resourceType} AS audit_resource_type), #{resourceId}, #{roomId}, #{messageId},
                    CAST(#{beforeState,typeHandler=com.multichat.infrastructure.mybatis.JsonMapTypeHandler} AS jsonb),
                    CAST(#{afterState,typeHandler=com.multichat.infrastructure.mybatis.JsonMapTypeHandler} AS jsonb),
                    CAST(#{detail,typeHandler=com.multichat.infrastructure.mybatis.JsonMapTypeHandler} AS jsonb), #{createdAt})
            """)
    int insert(AuditLog auditLog);

    @Select("""
            <script>
            SELECT id, request_id, actor_id, action::text AS action, resource_type::text AS resource_type,
                   resource_id, room_id, message_id, before_state, after_state, detail, created_at
            FROM audit_logs
            WHERE 1 = 1
            <if test="actorId != null"> AND actor_id = #{actorId} </if>
            <if test="roomId != null"> AND room_id = #{roomId} </if>
            <if test="messageId != null"> AND message_id = #{messageId} </if>
            <if test="action != null"> AND action = CAST(#{action} AS audit_action) </if>
            <if test="from != null"> AND created_at >= #{from} </if>
            <if test="to != null"> AND created_at &lt;= #{to} </if>
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = UUID.class, id = true),
            @Arg(column = "request_id", javaType = UUID.class),
            @Arg(column = "actor_id", javaType = UUID.class),
            @Arg(column = "action", javaType = String.class),
            @Arg(column = "resource_type", javaType = String.class),
            @Arg(column = "resource_id", javaType = UUID.class),
            @Arg(column = "room_id", javaType = UUID.class),
            @Arg(column = "message_id", javaType = UUID.class),
            @Arg(column = "before_state", javaType = Map.class, typeHandler = com.multichat.infrastructure.mybatis.JsonMapTypeHandler.class),
            @Arg(column = "after_state", javaType = Map.class, typeHandler = com.multichat.infrastructure.mybatis.JsonMapTypeHandler.class),
            @Arg(column = "detail", javaType = Map.class, typeHandler = com.multichat.infrastructure.mybatis.JsonMapTypeHandler.class),
            @Arg(column = "created_at", javaType = Instant.class)
    })
    List<AuditLog> find(@Param("actorId") UUID actorId, @Param("roomId") UUID roomId,
                        @Param("messageId") UUID messageId, @Param("action") String action,
                        @Param("from") Instant from, @Param("to") Instant to,
                        @Param("limit") int limit, @Param("offset") int offset);
}
