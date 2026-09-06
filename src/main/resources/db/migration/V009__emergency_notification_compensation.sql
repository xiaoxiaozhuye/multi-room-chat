-- Emergency notifications are committed as PUBLISHED so they bypass the
-- ordinary room_seq gate.  This small durable outbox records deliveries which
-- still require an at-least-once WebSocket retry.
CREATE TABLE emergency_notification_compensations (
    message_id UUID PRIMARY KEY REFERENCES messages(id) ON DELETE RESTRICT,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX ix_emergency_notification_compensations_updated
    ON emergency_notification_compensations (updated_at ASC, message_id ASC);
