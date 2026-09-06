package com.multichat.infrastructure.mapper;

import com.multichat.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;
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
            SELECT id, request_id, actor_id, action::text AS action, resource_type::text AS resource_type,
                   resource_id, room_id, message_id, before_state, after_state, detail, created_at
            FROM audit_logs
            WHERE (#{actorId} IS NULL OR actor_id = #{actorId})
              AND (#{roomId} IS NULL OR room_id = #{roomId})
              AND (#{messageId} IS NULL OR message_id = #{messageId})
              AND (#{action} IS NULL OR action = CAST(#{action} AS audit_action))
              AND (#{from} IS NULL OR created_at >= #{from})
              AND (#{to} IS NULL OR created_at <= #{to})
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    @Results(id = "auditLogResult", value = {
            @Result(column = "before_state", property = "beforeState", typeHandler = com.multichat.infrastructure.mybatis.JsonMapTypeHandler.class),
            @Result(column = "after_state", property = "afterState", typeHandler = com.multichat.infrastructure.mybatis.JsonMapTypeHandler.class),
            @Result(column = "detail", property = "detail", typeHandler = com.multichat.infrastructure.mybatis.JsonMapTypeHandler.class)
    })
    List<AuditLog> find(@Param("actorId") UUID actorId, @Param("roomId") UUID roomId,
                        @Param("messageId") UUID messageId, @Param("action") String action,
                        @Param("from") Instant from, @Param("to") Instant to,
                        @Param("limit") int limit, @Param("offset") int offset);
}
