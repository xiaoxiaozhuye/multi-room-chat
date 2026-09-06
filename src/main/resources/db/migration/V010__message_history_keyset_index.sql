-- Supports a user's room-scoped keyset history without scanning other senders'
-- messages.  The room history query is already covered by
-- ix_messages_room_seq_published from V003.
CREATE INDEX IF NOT EXISTS ix_messages_sender_room_seq_chat
    ON messages (sender_id, room_id, room_seq DESC)
    WHERE message_type = 'CHAT' AND room_seq IS NOT NULL;
