CREATE OR REPLACE FUNCTION validate_message_lifecycle()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF (NEW.message_type = 'CHAT' AND NEW.status NOT IN ('PENDING_REVIEW', 'APPROVED'))
           OR (NEW.message_type = 'ADMIN_MESSAGE' AND NEW.status <> 'APPROVED')
           OR (NEW.message_type = 'SYSTEM_NOTIFICATION' AND NEW.status <> 'PUBLISHED') THEN
            RAISE EXCEPTION 'invalid initial status % for message type %', NEW.status, NEW.message_type;
        END IF;
        IF NEW.message_type = 'SYSTEM_NOTIFICATION' AND NEW.published_at IS NULL THEN
            NEW.published_at := NEW.created_at;
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.room_id <> OLD.room_id OR NEW.sender_id <> OLD.sender_id OR NEW.request_id <> OLD.request_id
       OR NEW.room_seq IS DISTINCT FROM OLD.room_seq OR NEW.notification_seq IS DISTINCT FROM OLD.notification_seq
       OR NEW.message_type <> OLD.message_type OR NEW.content <> OLD.content OR NEW.created_at <> OLD.created_at
       OR NEW.review_deadline_at IS DISTINCT FROM OLD.review_deadline_at THEN
        RAISE EXCEPTION 'message identity, ordering and content are immutable';
    END IF;

    IF NEW.status = OLD.status THEN
        IF NEW.reviewed_at IS DISTINCT FROM OLD.reviewed_at
           OR NEW.reviewed_by IS DISTINCT FROM OLD.reviewed_by
           OR NEW.published_at IS DISTINCT FROM OLD.published_at THEN
            RAISE EXCEPTION 'message review and publication fields require a state transition';
        END IF;
        RETURN NEW;
    ELSIF OLD.message_type = 'CHAT' AND OLD.status = 'PENDING_REVIEW'
          AND NEW.status IN ('APPROVED', 'REJECTED', 'TIMEOUT', 'CANCELLED_BY_ROOM_DELETION') THEN
        NULL;
    ELSIF OLD.message_type = 'CHAT' AND OLD.status = 'APPROVED'
          AND NEW.status IN ('PUBLISHED', 'CANCELLED_BY_ROOM_DELETION') THEN
        NULL;
    ELSIF OLD.message_type = 'ADMIN_MESSAGE' AND OLD.status = 'APPROVED' AND NEW.status = 'PUBLISHED' THEN
        NULL;
    ELSE
        RAISE EXCEPTION 'illegal message transition: % -> %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$;

DO $$
DECLARE
    constraint_record RECORD;
BEGIN
    FOR constraint_record IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'messages'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%message_type = ''CHAT''%'
          AND (pg_get_constraintdef(oid) LIKE '%review_deadline_at IS NOT NULL%'
               OR pg_get_constraintdef(oid) LIKE '%status IN%')
    LOOP
        EXECUTE format('ALTER TABLE messages DROP CONSTRAINT %I', constraint_record.conname);
    END LOOP;
END;
$$;

ALTER TABLE messages
    ADD CONSTRAINT messages_chat_review_deadline_check CHECK (
        (message_type = 'CHAT' AND (review_deadline_at IS NOT NULL
            OR status IN ('APPROVED', 'PUBLISHED', 'CANCELLED_BY_ROOM_DELETION')))
        OR (message_type IN ('ADMIN_MESSAGE', 'SYSTEM_NOTIFICATION') AND review_deadline_at IS NULL)
    ),
    ADD CONSTRAINT messages_chat_approval_review_check CHECK (
        NOT (message_type = 'CHAT' AND status IN ('APPROVED', 'REJECTED'))
        OR (status = 'APPROVED' AND review_deadline_at IS NULL
            AND reviewed_at IS NULL AND reviewed_by IS NULL)
        OR (reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL)
    );
