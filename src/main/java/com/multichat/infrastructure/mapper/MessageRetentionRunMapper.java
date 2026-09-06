package com.multichat.infrastructure.mapper;

import com.multichat.message.retention.MessageRetentionRun;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.UUID;

@Mapper
public interface MessageRetentionRunMapper {
    @Insert("""
            INSERT INTO message_retention_runs (id, trigger_type, requested_by, cutoff_at, status, started_at)
            VALUES (#{run.id}, #{run.triggerType}, #{run.requestedBy}, #{run.cutoffAt}, #{run.status}, #{run.startedAt})
            """)
    int insert(@Param("run") MessageRetentionRun run);

    @Update("""
            UPDATE message_retention_runs
            SET batch_count = batch_count + #{batchCount}, purged_count = purged_count + #{purgedCount},
                retry_count = retry_count + #{retryCount}, last_error = COALESCE(#{lastError}, last_error)
            WHERE id = #{runId} AND status = 'RUNNING'
            """)
    int addProgress(@Param("runId") UUID runId, @Param("batchCount") int batchCount,
                    @Param("purgedCount") int purgedCount, @Param("retryCount") int retryCount,
                    @Param("lastError") String lastError);

    @Update("""
            UPDATE message_retention_runs
            SET status = #{status}, completed_at = #{completedAt},
                last_error = COALESCE(#{lastError}, last_error)
            WHERE id = #{runId} AND status = 'RUNNING'
            """)
    int complete(@Param("runId") UUID runId, @Param("status") String status,
                 @Param("completedAt") Instant completedAt, @Param("lastError") String lastError);
}
