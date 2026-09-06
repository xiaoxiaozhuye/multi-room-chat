-- Execute after V001..V004. This script leaves no data behind.
BEGIN;

DO $$
DECLARE
    v_request_id UUID := '50000000-0000-0000-0000-000000000001';
    v_message_id UUID := '50000000-0000-0000-0000-000000000002';
    v_pending_membership_id UUID := '50000000-0000-0000-0000-000000000003';
    v_illegal_state_blocked BOOLEAN := false;
BEGIN
    -- PENDING plus ACTIVE for one user/room is rejected, while terminal history remains possible.
    INSERT INTO user_chat_rooms (id, user_id, room_id, status)
    VALUES (
        v_pending_membership_id,
        '00000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000002',
        'PENDING'
    );
    BEGIN
        INSERT INTO user_chat_rooms (id, user_id, room_id, status)
        VALUES (
            '50000000-0000-0000-0000-000000000004',
            '00000000-0000-0000-0000-000000000001',
            '10000000-0000-0000-0000-000000000002',
            'ACTIVE'
        );
        RAISE EXCEPTION 'expected duplicate live membership to fail';
    EXCEPTION WHEN unique_violation THEN
        NULL;
    END;

    -- The trigger assigns room_seq = 1. A duplicate request rolls back its attempted counter increment.
    INSERT INTO messages (id, request_id, room_id, sender_id, message_type, content, status, review_deadline_at)
    VALUES (
        v_message_id, v_request_id,
        '10000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000001',
        'CHAT', 'first submission', 'PENDING_REVIEW', clock_timestamp() + interval '30 seconds'
    );
    BEGIN
        INSERT INTO messages (id, request_id, room_id, sender_id, message_type, content, status, review_deadline_at)
        VALUES (
            '50000000-0000-0000-0000-000000000005', v_request_id,
            '10000000-0000-0000-0000-000000000001',
            '00000000-0000-0000-0000-000000000001',
            'CHAT', 'retry', 'PENDING_REVIEW', clock_timestamp() + interval '30 seconds'
        );
        RAISE EXCEPTION 'expected duplicate sender request_id to fail';
    EXCEPTION WHEN unique_violation THEN
        NULL;
    END;
    IF (SELECT room_seq FROM messages WHERE id = v_message_id) <> 1
       OR (SELECT next_room_seq FROM chat_rooms WHERE id = '10000000-0000-0000-0000-000000000001') <> 1 THEN
        RAISE EXCEPTION 'room sequence allocation is not atomic';
    END IF;

    -- A CHAT cannot be born published or bypass PENDING_REVIEW.
    BEGIN
        INSERT INTO messages (id, request_id, room_id, sender_id, message_type, content, status, review_deadline_at, published_at)
        VALUES (
            '50000000-0000-0000-0000-000000000006',
            '50000000-0000-0000-0000-000000000007',
            '10000000-0000-0000-0000-000000000001',
            '00000000-0000-0000-0000-000000000001',
            'CHAT', 'illegal state', 'PUBLISHED', clock_timestamp() + interval '30 seconds', clock_timestamp()
        );
    EXCEPTION WHEN raise_exception THEN
        v_illegal_state_blocked := true;
    END;
    IF NOT v_illegal_state_blocked THEN
        RAISE EXCEPTION 'expected illegal initial message status to fail';
    END IF;
END;
$$;

ROLLBACK;
