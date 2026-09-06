package com.multichat.infrastructure.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Durable retry queue for already-published emergency notifications. */
@Mapper
public interface EmergencyNotificationCompensationMapper {
    @Insert("""
            INSERT INTO emergency_notification_compensations (message_id, created_at, updated_at)
            VALUES (#{messageId}, #{now}, #{now})
            ON CONFLICT (message_id) DO NOTHING
            """)
    int enqueue(@Param("messageId") UUID messageId, @Param("now") Instant now);

    @Select("""
            SELECT message_id
            FROM emergency_notification_compensations
            ORDER BY updated_at ASC, message_id ASC
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<UUID> lockNextMessageIds(@Param("limit") int limit);

    @Delete("DELETE FROM emergency_notification_compensations WHERE message_id = #{messageId}")
    int remove(@Param("messageId") UUID messageId);

    @Update("""
            UPDATE emergency_notification_compensations
            SET attempts = attempts + 1, last_attempt_at = #{attemptedAt}, updated_at = #{attemptedAt}
            WHERE message_id = #{messageId}
            """)
    int recordFailure(@Param("messageId") UUID messageId, @Param("attemptedAt") Instant attemptedAt);
}
