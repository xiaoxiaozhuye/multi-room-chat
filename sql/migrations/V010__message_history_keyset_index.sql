-- Mirrors the application Flyway migration for standalone SQL deployment.
CREATE INDEX IF NOT EXISTS ix_messages_sender_room_seq_chat
    ON messages (sender_id, room_id, room_seq DESC)
    WHERE message_type = 'CHAT' AND room_seq IS NOT NULL;
