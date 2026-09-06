package com.multichat.infrastructure.mapper;

import com.multichat.message.entity.ChatMessage;
import com.multichat.message.entity.PendingReviewMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface MessageMapper {
    @Select("""
            SELECT id, request_id, room_id, sender_id, room_seq, notification_seq,
                   message_type::text AS message_type, content, status::text AS status,
                   review_deadline_at, reviewed_at, published_at, version, created_at
            FROM messages WHERE id = #{messageId}
            """)
    Optional<ChatMessage> findById(UUID messageId);

    /** PostgreSQL remains authoritative for both recovery and Redis-down scans. */
    @Select("""
            SELECT id, review_deadline_at
            FROM messages
            WHERE status = CAST('PENDING_REVIEW' AS message_status)
              AND review_deadline_at IS NOT NULL
            ORDER BY review_deadline_at ASC, id ASC
            """)
    List<PendingReviewMessage> findPendingReview();

    @Select("""
            SELECT id, review_deadline_at
            FROM messages
            WHERE status = CAST('PENDING_REVIEW' AS message_status)
              AND review_deadline_at IS NOT NULL
              AND review_deadline_at <= #{dueAt}
            ORDER BY review_deadline_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<PendingReviewMessage> findPendingReviewDue(@Param("dueAt") Instant dueAt,
                                                    @Param("limit") int limit);

    /** Retains messages for audit while making all unpublished ordinary messages terminal. */
    @Update("""
            UPDATE messages
            SET status = CAST('CANCELLED_BY_ROOM_DELETION' AS message_status), version = version + 1,
                updated_at = #{deletedAt}
            WHERE room_id = #{roomId}
              AND message_type IN (CAST('CHAT' AS message_type), CAST('ADMIN_MESSAGE' AS message_type))
              AND status IN (CAST('PENDING_REVIEW' AS message_status), CAST('APPROVED' AS message_status))
            """)
    int cancelUnpublishedByRoomDeletion(@Param("roomId") UUID roomId, @Param("deletedAt") Instant deletedAt);
}
