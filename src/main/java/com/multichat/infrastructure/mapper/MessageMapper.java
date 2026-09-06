package com.multichat.infrastructure.mapper;

import com.multichat.message.entity.ChatMessage;
import com.multichat.message.entity.PendingReviewMessage;
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
public interface MessageMapper {
    @Select("""
            SELECT id, request_id, room_id, sender_id, room_seq, notification_seq,
                   message_type::text AS message_type, content, status::text AS status,
                   review_deadline_at, reviewed_at, published_at, version, created_at
            FROM messages
            WHERE sender_id = #{senderId} AND request_id = #{requestId}
            """)
    Optional<ChatMessage> findBySenderAndRequestId(@Param("senderId") UUID senderId,
                                                    @Param("requestId") UUID requestId);

    /**
     * The database trigger atomically locks the room and assigns room_seq.  DO
     * The caller serializes a sender's submit/retry race before this statement,
     * so the trigger is never run for a duplicate request.
     */
    @Insert("""
            INSERT INTO messages (id, request_id, room_id, sender_id, message_type, content, status,
                                  review_deadline_at, version, created_at, updated_at)
            VALUES (#{message.id}, #{message.requestId}, #{message.roomId}, #{message.senderId},
                    CAST('CHAT' AS message_type), #{message.content}, CAST('PENDING_REVIEW' AS message_status),
                    #{message.reviewDeadlineAt}, 0, #{message.createdAt}, #{message.createdAt})
            """)
    int insertPendingChat(@Param("message") ChatMessage message);

    /**
     * Administrative messages enter the same ordered room stream as CHAT, but
     * do not enter manual review.  The database trigger remains responsible
     * for assigning room_seq and re-checking the grant under the room lock.
     */
    @Insert("""
            INSERT INTO messages (id, request_id, room_id, sender_id, message_type, content, status,
                                  version, created_at, updated_at)
            VALUES (#{message.id}, #{message.requestId}, #{message.roomId}, #{message.senderId},
                    CAST('ADMIN_MESSAGE' AS message_type), #{message.content},
                    CAST('APPROVED' AS message_status), 0, #{message.createdAt}, #{message.createdAt})
            """)
    int insertApprovedAdminMessage(@Param("message") ChatMessage message);

    /**
     * Emergency notifications have their own sequence and are durable before
     * any socket is touched.  A failed first delivery is tracked separately by
     * the compensation queue rather than rolling this row back.
     */
    @Insert("""
            INSERT INTO messages (id, request_id, room_id, sender_id, message_type, content, status,
                                  published_at, version, created_at, updated_at)
            VALUES (#{message.id}, #{message.requestId}, #{message.roomId}, #{message.senderId},
                    CAST('SYSTEM_NOTIFICATION' AS message_type), #{message.content},
                    CAST('PUBLISHED' AS message_status), #{message.publishedAt}, 0,
                    #{message.createdAt}, #{message.createdAt})
            """)
    int insertPublishedEmergencyNotification(@Param("message") ChatMessage message);

    @Select("""
            SELECT id, request_id, room_id, sender_id, room_seq, notification_seq,
                   message_type::text AS message_type, content, status::text AS status,
                   review_deadline_at, reviewed_at, published_at, version, created_at
            FROM messages WHERE id = #{messageId}
            """)
    Optional<ChatMessage> findById(UUID messageId);

    /**
     * Keyset history query for room readers.  Its status predicate is the
     * visibility boundary: messages awaiting (or failing) review can never be
     * returned through the public room-history API.
     */
    @Select("""
            SELECT id, request_id, room_id, sender_id, room_seq, notification_seq,
                   message_type::text AS message_type, content, status::text AS status,
                   review_deadline_at, reviewed_at, published_at, version, created_at
            FROM messages
            WHERE room_id = #{roomId}
              AND status = CAST('PUBLISHED' AS message_status)
              AND message_type IN (CAST('CHAT' AS message_type), CAST('ADMIN_MESSAGE' AS message_type))
              AND (#{beforeSeq} IS NULL OR room_seq < #{beforeSeq})
            ORDER BY room_seq DESC, id DESC
            LIMIT #{limit}
            """)
    List<ChatMessage> findPublishedChatHistoryBefore(@Param("roomId") UUID roomId,
                                                      @Param("beforeSeq") Long beforeSeq,
                                                      @Param("limit") int limit);

    /** Personal history deliberately does not join room membership: a user can
     * still inspect the disposition of messages they submitted before leaving. */
    @Select("""
            SELECT id, request_id, room_id, sender_id, room_seq, notification_seq,
                   message_type::text AS message_type, content, status::text AS status,
                   review_deadline_at, reviewed_at, published_at, version, created_at
            FROM messages
            WHERE room_id = #{roomId}
              AND sender_id = #{senderId}
              AND message_type = CAST('CHAT' AS message_type)
              AND (#{beforeSeq} IS NULL OR room_seq < #{beforeSeq})
            ORDER BY room_seq DESC, id DESC
            LIMIT #{limit}
            """)
    List<ChatMessage> findOwnChatHistoryBefore(@Param("senderId") UUID senderId,
                                                @Param("roomId") UUID roomId,
                                                @Param("beforeSeq") Long beforeSeq,
                                                @Param("limit") int limit);

    /** Querying by both id and sender avoids leaking the existence or review
     * status of another user's unpublished message. */
    @Select("""
            SELECT id, request_id, room_id, sender_id, room_seq, notification_seq,
                   message_type::text AS message_type, content, status::text AS status,
                   review_deadline_at, reviewed_at, published_at, version, created_at
            FROM messages
            WHERE id = #{messageId}
              AND sender_id = #{senderId}
              AND message_type = CAST('CHAT' AS message_type)
            """)
    Optional<ChatMessage> findOwnChatById(@Param("senderId") UUID senderId,
                                           @Param("messageId") UUID messageId);

    /**
     * Review writes are deliberately conditional.  The status predicate is the
     * concurrency boundary shared by administrators and the timeout worker.
     * For a room administrator the permission predicate is evaluated by the
     * database at the same time as the state transition, closing a grant/revoke
     * race between the Java authorization check and this update.
     */
    @Update("""
            UPDATE messages
            SET status = CAST(#{targetStatus} AS message_status), reviewed_at = #{reviewedAt},
                reviewed_by = #{reviewerId}, version = version + 1, updated_at = #{reviewedAt}
            WHERE id = #{messageId}
              AND message_type = CAST('CHAT' AS message_type)
              AND status = CAST('PENDING_REVIEW' AS message_status)
              AND (#{systemAdmin} = TRUE OR EXISTS (
                  SELECT 1 FROM admin_room_permissions permission
                  WHERE permission.admin_id = #{reviewerId}
                    AND permission.room_id = messages.room_id
                    AND permission.revoked_at IS NULL
              ))
            """)
    int reviewPending(@Param("messageId") UUID messageId, @Param("targetStatus") String targetStatus,
                      @Param("reviewerId") UUID reviewerId, @Param("systemAdmin") boolean systemAdmin,
                      @Param("reviewedAt") Instant reviewedAt);

    /**
     * The timeout worker shares the PENDING_REVIEW predicate with human
     * review. Whichever conditional update commits first owns the terminal
     * state, so a stale Redis member can never overwrite a human decision.
     */
    @Update("""
            UPDATE messages
            SET status = CAST('TIMEOUT' AS message_status), reviewed_at = #{timedOutAt},
                version = version + 1, updated_at = #{timedOutAt}
            WHERE id = #{messageId}
              AND message_type = CAST('CHAT' AS message_type)
              AND status = CAST('PENDING_REVIEW' AS message_status)
              AND review_deadline_at IS NOT NULL
              AND review_deadline_at <= #{timedOutAt}
            """)
    int timeoutPending(@Param("messageId") UUID messageId, @Param("timedOutAt") Instant timedOutAt);

    /** Lists only ordinary messages visible to the requesting room administrator. */
    @Select("""
            SELECT m.id, m.request_id, m.room_id, m.sender_id, m.room_seq, m.notification_seq,
                   m.message_type::text AS message_type, m.content, m.status::text AS status,
                   m.review_deadline_at, m.reviewed_at, m.published_at, m.version, m.created_at
            FROM messages m
            WHERE m.message_type = CAST('CHAT' AS message_type)
              AND m.content_retired_at IS NULL
              AND (#{roomId} IS NULL OR m.room_id = #{roomId})
              AND (#{senderId} IS NULL OR m.sender_id = #{senderId})
              AND (#{status} IS NULL OR m.status = CAST(#{status} AS message_status))
              AND (#{createdFrom} IS NULL OR m.created_at >= #{createdFrom})
              AND (#{createdTo} IS NULL OR m.created_at <= #{createdTo})
              AND (#{authorizedAdminId} IS NULL OR EXISTS (
                  SELECT 1 FROM admin_room_permissions permission
                  WHERE permission.admin_id = #{authorizedAdminId}
                    AND permission.room_id = m.room_id
                    AND permission.revoked_at IS NULL
              ))
            ORDER BY m.created_at ASC, m.room_seq ASC, m.id ASC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<ChatMessage> findReviewMessages(@Param("roomId") UUID roomId, @Param("senderId") UUID senderId,
                                         @Param("status") String status,
                                         @Param("createdFrom") Instant createdFrom,
                                         @Param("createdTo") Instant createdTo,
                                         @Param("authorizedAdminId") UUID authorizedAdminId,
                                         @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT published_message_id, published_room_seq
            FROM drain_room_publish_queue(#{roomId})
            """)
    List<PublishedMessage> drainRoomPublishQueue(@Param("roomId") UUID roomId);

    /**
     * Serializes every normal-message publish attempt for a room.  The lock is
     * deliberately held until the WebSocket attempt and its state transition
     * have completed, so room_seq can never be delivered out of order.
     */
    @Select("""
            SELECT id FROM chat_rooms
            WHERE id = #{roomId} AND deleted_at IS NULL
            FOR UPDATE
            """)
    Optional<UUID> lockRoomForPublication(@Param("roomId") UUID roomId);

    @Select("""
            SELECT id, request_id, room_id, sender_id, room_seq, notification_seq,
                   message_type::text AS message_type, content, status::text AS status,
                   review_deadline_at, reviewed_at, published_at, version, created_at
            FROM messages
            WHERE room_id = #{roomId}
              AND room_seq = (SELECT next_publish_seq FROM chat_rooms WHERE id = #{roomId})
            FOR UPDATE
            """)
    Optional<ChatMessage> findNextRoomPublishCandidateForUpdate(@Param("roomId") UUID roomId);

    @Update("""
            UPDATE chat_rooms
            SET next_publish_seq = next_publish_seq + 1, version = version + 1
            WHERE id = #{roomId} AND next_publish_seq = #{roomSeq}
            """)
    int advanceRoomPublishCursor(@Param("roomId") UUID roomId, @Param("roomSeq") long roomSeq);

    @Update("""
            UPDATE messages
            SET status = CAST('PUBLISHED' AS message_status), published_at = #{publishedAt},
                version = version + 1, updated_at = #{publishedAt}
            WHERE id = #{messageId}
              AND status = CAST('APPROVED' AS message_status)
            """)
    int markApprovedPublished(@Param("messageId") UUID messageId, @Param("publishedAt") Instant publishedAt);

    @Select("""
            SELECT DISTINCT room_id
            FROM messages
            WHERE status = CAST('APPROVED' AS message_status)
              AND message_type IN (CAST('CHAT' AS message_type), CAST('ADMIN_MESSAGE' AS message_type))
            ORDER BY room_id
            LIMIT #{limit}
            """)
    List<UUID> findRoomsWithApprovedMessages(@Param("limit") int limit);

    @Select("""
            SELECT COALESCE(max(room_seq), 0) FROM messages
            WHERE room_id = #{roomId} AND status = CAST('PUBLISHED' AS message_status)
              AND message_type IN (CAST('CHAT' AS message_type), CAST('ADMIN_MESSAGE' AS message_type))
            """)
    long highestPublishedRoomSeq(@Param("roomId") UUID roomId);

    /** Earliest replayable sequence; null means no retained body is available. */
    @Select("""
            SELECT min(room_seq) FROM messages
            WHERE room_id = #{roomId} AND status = CAST('PUBLISHED' AS message_status)
              AND content_retired_at IS NULL
              AND message_type IN (CAST('CHAT' AS message_type), CAST('ADMIN_MESSAGE' AS message_type))
            """)
    Long earliestRetainedRoomSeq(@Param("roomId") UUID roomId);

    @Select("""
            SELECT COALESCE(max(notification_seq), 0) FROM messages
            WHERE room_id = #{roomId} AND status = CAST('PUBLISHED' AS message_status)
              AND message_type = CAST('SYSTEM_NOTIFICATION' AS message_type)
            """)
    long highestPublishedNotificationSeq(@Param("roomId") UUID roomId);

    @Select("""
            SELECT min(notification_seq) FROM messages
            WHERE room_id = #{roomId} AND status = CAST('PUBLISHED' AS message_status)
              AND content_retired_at IS NULL
              AND message_type = CAST('SYSTEM_NOTIFICATION' AS message_type)
            """)
    Long earliestRetainedNotificationSeq(@Param("roomId") UUID roomId);

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM messages
                WHERE room_id = #{roomId} AND status = CAST('PUBLISHED' AS message_status)
                  AND content_retired_at IS NOT NULL
                  AND message_type IN (CAST('CHAT' AS message_type), CAST('ADMIN_MESSAGE' AS message_type))
                  AND room_seq > #{afterSeq} AND room_seq <= #{toSeq}
            )
            """)
    boolean hasRetiredChatInRange(@Param("roomId") UUID roomId, @Param("afterSeq") long afterSeq,
                                  @Param("toSeq") long toSeq);

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM messages
                WHERE room_id = #{roomId} AND status = CAST('PUBLISHED' AS message_status)
                  AND content_retired_at IS NOT NULL
                  AND message_type = CAST('SYSTEM_NOTIFICATION' AS message_type)
                  AND notification_seq > #{afterSeq} AND notification_seq <= #{toSeq}
            )
            """)
    boolean hasRetiredNotificationInRange(@Param("roomId") UUID roomId, @Param("afterSeq") long afterSeq,
                                          @Param("toSeq") long toSeq);

    @Select("""
            SELECT id, request_id, room_id, sender_id, room_seq, notification_seq,
                   message_type::text AS message_type, content, status::text AS status,
                   review_deadline_at, reviewed_at, published_at, version, created_at
            FROM messages
            WHERE room_id = #{roomId} AND status = CAST('PUBLISHED' AS message_status)
              AND content_retired_at IS NULL
              AND message_type IN (CAST('CHAT' AS message_type), CAST('ADMIN_MESSAGE' AS message_type))
              AND room_seq > #{afterSeq} AND room_seq <= #{toSeq}
            ORDER BY room_seq ASC, id ASC
            """)
    List<ChatMessage> findPublishedChatRange(@Param("roomId") UUID roomId,
                                              @Param("afterSeq") long afterSeq, @Param("toSeq") long toSeq);

    @Select("""
            SELECT id, request_id, room_id, sender_id, room_seq, notification_seq,
                   message_type::text AS message_type, content, status::text AS status,
                   review_deadline_at, reviewed_at, published_at, version, created_at
            FROM messages
            WHERE room_id = #{roomId} AND status = CAST('PUBLISHED' AS message_status)
              AND content_retired_at IS NULL
              AND message_type = CAST('SYSTEM_NOTIFICATION' AS message_type)
              AND notification_seq > #{afterSeq} AND notification_seq <= #{toSeq}
            ORDER BY notification_seq ASC, id ASC
            """)
    List<ChatMessage> findPublishedNotificationRange(@Param("roomId") UUID roomId,
                                                      @Param("afterSeq") long afterSeq, @Param("toSeq") long toSeq);

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

    @Select("""
            SELECT count(*) FROM messages
            WHERE message_type = CAST('CHAT' AS message_type)
              AND status = CAST('PENDING_REVIEW' AS message_status)
            """)
    long countPendingReviews();

    @Select("""
            SELECT count(*) FROM messages
            WHERE created_at >= (date_trunc('day', now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC')
            """)
    long countMessagesCreatedTodayUtc();

    @Select("""
            SELECT COALESCE(avg(EXTRACT(EPOCH FROM (reviewed_at - created_at))), 0)
            FROM messages
            WHERE message_type = CAST('CHAT' AS message_type)
              AND reviewed_at IS NOT NULL
            """)
    double averageReviewDurationSeconds();

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

    /** Locks a small, deterministic candidate page; SKIP LOCKED permits safe multi-node runs. */
    @Select("""
            SELECT id, request_id, room_id, sender_id, room_seq, notification_seq,
                   message_type::text AS message_type, content, status::text AS status,
                   review_deadline_at, reviewed_at, published_at, version, created_at
            FROM messages
            WHERE content_retired_at IS NULL
              AND content IS NOT NULL
              AND created_at < #{cutoffAt}
              AND status IN (CAST('PUBLISHED' AS message_status), CAST('REJECTED' AS message_status),
                             CAST('TIMEOUT' AS message_status), CAST('CANCELLED_BY_ROOM_DELETION' AS message_status))
            ORDER BY created_at ASC, id ASC
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<ChatMessage> lockRetentionCandidates(@Param("cutoffAt") Instant cutoffAt, @Param("limit") int limit);

    /** Conditional write makes retries idempotent and can never clear a post-cutoff message. */
    @Update("""
            UPDATE messages
            SET content = NULL, content_retired_at = #{retiredAt}, version = version + 1, updated_at = #{retiredAt}
            WHERE id = #{messageId}
              AND content_retired_at IS NULL
              AND content IS NOT NULL
              AND created_at < #{cutoffAt}
              AND status IN (CAST('PUBLISHED' AS message_status), CAST('REJECTED' AS message_status),
                             CAST('TIMEOUT' AS message_status), CAST('CANCELLED_BY_ROOM_DELETION' AS message_status))
            """)
    int retireContent(@Param("messageId") UUID messageId, @Param("cutoffAt") Instant cutoffAt,
                      @Param("retiredAt") Instant retiredAt);
}
