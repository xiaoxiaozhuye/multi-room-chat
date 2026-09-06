-- Keep the standalone migration set aligned with the runtime Flyway migrations.
CREATE OR REPLACE FUNCTION drain_room_publish_queue(p_room_id UUID)
RETURNS TABLE (published_message_id UUID, published_room_seq BIGINT)
LANGUAGE plpgsql
AS $$
DECLARE
    v_next_seq BIGINT;
    v_message_id UUID;
    v_status message_status;
BEGIN
    LOOP
        SELECT next_publish_seq INTO v_next_seq FROM chat_rooms
        WHERE id = p_room_id AND deleted_at IS NULL FOR UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION 'room % does not exist or is deleted', p_room_id; END IF;
        SELECT id, status INTO v_message_id, v_status FROM messages
        WHERE room_id = p_room_id AND room_seq = v_next_seq FOR UPDATE;
        IF NOT FOUND THEN RETURN; END IF;
        IF v_status IN ('REJECTED', 'TIMEOUT', 'CANCELLED_BY_ROOM_DELETION', 'PUBLISHED') THEN
            UPDATE chat_rooms SET next_publish_seq = next_publish_seq + 1, version = version + 1
            WHERE id = p_room_id;
        ELSIF v_status = 'APPROVED' THEN
            published_message_id := v_message_id;
            published_room_seq := v_next_seq;
            RETURN NEXT;
            RETURN;
        ELSE
            RETURN;
        END IF;
    END LOOP;
END;
$$;

CREATE INDEX IF NOT EXISTS ix_messages_approved_room_seq
    ON messages (room_id, room_seq)
    WHERE status = 'APPROVED' AND room_seq IS NOT NULL;
