package com.multichat.infrastructure.mapper;

import com.multichat.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper {
    @Insert("""
            INSERT INTO audit_logs (id, request_id, actor_id, action, resource_type, resource_id, room_id, message_id, created_at)
            VALUES (#{id}, #{requestId}, #{actorId}, CAST(#{action} AS audit_action),
                    CAST(#{resourceType} AS audit_resource_type), #{resourceId}, #{roomId}, #{messageId}, #{createdAt})
            """)
    int insert(AuditLog auditLog);
}
