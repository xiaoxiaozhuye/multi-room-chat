-- Kept in sync with the Flyway classpath migration.
ALTER TABLE messages ALTER COLUMN content DROP NOT NULL;
ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_content_check;
ALTER TABLE messages
    ADD COLUMN content_retired_at TIMESTAMPTZ,
    ADD CONSTRAINT messages_content_retired_consistency_check
        CHECK ((content IS NULL) = (content_retired_at IS NOT NULL));
CREATE INDEX ix_messages_retention_candidates
    ON messages (created_at, id)
    WHERE content_retired_at IS NULL
      AND status IN ('PUBLISHED', 'REJECTED', 'TIMEOUT', 'CANCELLED_BY_ROOM_DELETION');
CREATE TABLE message_retention_runs (
    id UUID PRIMARY KEY,
    trigger_type VARCHAR(16) NOT NULL CHECK (trigger_type IN ('SCHEDULED', 'MANUAL')),
    requested_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    cutoff_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    batch_count INTEGER NOT NULL DEFAULT 0 CHECK (batch_count >= 0),
    purged_count INTEGER NOT NULL DEFAULT 0 CHECK (purged_count >= 0),
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    last_error TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    completed_at TIMESTAMPTZ
);
CREATE INDEX ix_message_retention_runs_started_at ON message_retention_runs (started_at DESC, id DESC);
ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'MESSAGE_CONTENT_PURGED';
ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'MESSAGE_RETENTION_RUN';
ALTER TYPE audit_resource_type ADD VALUE IF NOT EXISTS 'MESSAGE_RETENTION_RUN';
