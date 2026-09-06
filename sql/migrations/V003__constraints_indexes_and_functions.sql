-- Case-insensitive identifiers stay unique even after a logical deletion.
CREATE UNIQUE INDEX ux_users_username_ci ON users (lower(username));
CREATE UNIQUE INDEX ux_users_email_ci ON users (lower(email));
CREATE UNIQUE INDEX ux_chat_rooms_name_active_ci ON chat_rooms (lower(name)) WHERE deleted_at IS NULL;

-- A user can have any number of finished applications, but only one live one.
CREATE UNIQUE INDEX ux_user_chat_rooms_one_live_membership
    ON user_chat_rooms (user_id, room_id)
    WHERE status IN ('PENDING', 'ACTIVE');

-- A permission is retained as history after revocation; only one active grant exists.
CREATE UNIQUE INDEX ux_admin_room_permissions_one_active
    ON admin_room_permissions (admin_id, room_id)
    WHERE revoked_at IS NULL;

-- Sender-scoped idempotency: retries return the original message instead of inserting another row.
CREATE UNIQUE INDEX ux_messages_sender_request_id ON messages (sender_id, request_id);
CREATE UNIQUE INDEX ux_messages_room_room_seq ON messages (room_id, room_seq) WHERE room_seq IS NOT NULL;
CREATE UNIQUE INDEX ux_messages_room_notification_seq ON messages (room_id, notification_seq) WHERE notification_seq IS NOT NULL;

-- Cursor pagination, room recovery, moderation and personal-history access paths.
CREATE INDEX ix_messages_room_seq_published ON messages (room_id, room_seq DESC) WHERE status = 'PUBLISHED' AND room_seq IS NOT NULL;
CREATE INDEX ix_messages_status_created_at ON messages (status, created_at DESC);
CREATE INDEX ix_messages_sender_created_at ON messages (sender_id, created_at DESC);
CREATE INDEX ix_messages_review_deadline ON messages (review_deadline_at) WHERE status = 'PENDING_REVIEW';
CREATE INDEX ix_user_chat_rooms_user_room ON user_chat_rooms (user_id, room_id, status, created_at DESC);
CREATE INDEX ix_user_chat_rooms_room_status ON user_chat_rooms (room_id, status, created_at DESC);
CREATE INDEX ix_audit_logs_room_created_at ON audit_logs (room_id, created_at DESC);
CREATE INDEX ix_audit_logs_message_created_at ON audit_logs (message_id, created_at DESC) WHERE message_id IS NOT NULL;
CREATE INDEX ix_audit_logs_actor_created_at ON audit_logs (actor_id, created_at DESC) WHERE actor_id IS NOT NULL;
CREATE INDEX ix_audit_logs_request_id ON audit_logs (request_id) WHERE request_id IS NOT NULL;
CREATE INDEX ix_audit_logs_detail_gin ON audit_logs USING GIN (detail);

CREATE OR REPLACE FUNCTION touch_versioned_row()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION 'optimistic-lock version must increase by exactly one for %', TG_TABLE_NAME
            USING ERRCODE = '40001';
    END IF;
    NEW.updated_at := clock_timestamp();
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION validate_membership_lifecycle()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status NOT IN ('PENDING', 'ACTIVE') THEN
            RAISE EXCEPTION 'membership must begin as PENDING or ACTIVE';
        END IF;
        IF NEW.status = 'ACTIVE' AND NEW.activated_at IS NULL THEN
            NEW.activated_at := NEW.requested_at;
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.status = OLD.status THEN
        RETURN NEW;
    ELSIF OLD.status = 'PENDING' AND NEW.status = 'ACTIVE' THEN
        NEW.activated_at := COALESCE(NEW.activated_at, clock_timestamp());
        NEW.resolved_at := COALESCE(NEW.resolved_at, clock_timestamp());
        IF NEW.resolved_by IS NULL THEN
            RAISE EXCEPTION 'approving a membership requires resolved_by';
        END IF;
    ELSIF OLD.status = 'PENDING' AND NEW.status = 'REJECTED' THEN
        NEW.resolved_at := COALESCE(NEW.resolved_at, clock_timestamp());
        IF NEW.resolved_by IS NULL THEN
            RAISE EXCEPTION 'rejecting a membership requires resolved_by';
        END IF;
    ELSIF OLD.status = 'ACTIVE' AND NEW.status = 'EXITED' THEN
        NEW.exited_at := COALESCE(NEW.exited_at, clock_timestamp());
    ELSE
        RAISE EXCEPTION 'illegal membership transition: % -> %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION validate_room_lifecycle()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'ACTIVE' OR NEW.deleted_at IS NOT NULL THEN
            RAISE EXCEPTION 'a room must begin ACTIVE and not deleted';
        END IF;
        RETURN NEW;
    END IF;

    IF OLD.deleted_at IS NOT NULL THEN
        RAISE EXCEPTION 'a logically deleted room cannot be changed';
    END IF;
    IF NEW.deleted_at IS NOT NULL THEN
        RETURN NEW;
    END IF;
    IF NEW.status = OLD.status THEN
        RETURN NEW;
    ELSIF OLD.status = 'ACTIVE' AND NEW.status IN ('PAUSED', 'CLOSED') THEN
        RETURN NEW;
    ELSIF OLD.status = 'PAUSED' AND NEW.status IN ('ACTIVE', 'CLOSED') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'illegal room transition: % -> %', OLD.status, NEW.status;
END;
$$;

CREATE OR REPLACE FUNCTION validate_admin_permission_grantee()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_role user_role;
BEGIN
    SELECT role INTO v_role FROM users WHERE id = NEW.admin_id AND deleted_at IS NULL AND status = 'ACTIVE';
    IF NOT FOUND OR v_role NOT IN ('ROOM_ADMIN', 'SYSTEM_ADMIN') THEN
        RAISE EXCEPTION 'admin_room_permissions.admin_id must reference an active administrator';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION validate_admin_permission_lifecycle()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.admin_id <> OLD.admin_id OR NEW.room_id <> OLD.room_id
       OR NEW.granted_by <> OLD.granted_by OR NEW.granted_at <> OLD.granted_at THEN
        RAISE EXCEPTION 'admin permission grant identity is immutable';
    END IF;
    IF OLD.revoked_at IS NOT NULL THEN
        RAISE EXCEPTION 'a revoked admin permission cannot be changed or reactivated';
    END IF;
    IF NEW.revoked_at IS NULL OR NEW.revoked_by IS NULL THEN
        RAISE EXCEPTION 'an active admin permission may only transition to revoked';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION validate_message_lifecycle()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF (NEW.message_type = 'CHAT' AND NEW.status <> 'PENDING_REVIEW')
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

CREATE OR REPLACE FUNCTION assign_message_sequence_and_validate_sender()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_room_status room_status;
    v_room_deleted_at TIMESTAMPTZ;
    v_sender_role user_role;
BEGIN
    -- This UPDATE locks the room row. Concurrent inserts into the same room serialize here,
    -- so assigned sequence values are strictly increasing and cannot be duplicated.
    SELECT status, deleted_at INTO v_room_status, v_room_deleted_at
    FROM chat_rooms WHERE id = NEW.room_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'room % does not exist', NEW.room_id USING ERRCODE = '23503';
    END IF;
    IF v_room_deleted_at IS NOT NULL THEN
        RAISE EXCEPTION 'cannot create a message in a deleted room';
    END IF;

    SELECT role INTO v_sender_role
    FROM users
    WHERE id = NEW.sender_id AND deleted_at IS NULL AND status = 'ACTIVE';
    IF NOT FOUND THEN
        RAISE EXCEPTION 'message sender does not exist or is deleted';
    END IF;

    IF NEW.message_type = 'CHAT' THEN
        IF v_room_status <> 'ACTIVE' THEN
            RAISE EXCEPTION 'CHAT messages require an ACTIVE room';
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM user_chat_rooms
            WHERE room_id = NEW.room_id AND user_id = NEW.sender_id AND status = 'ACTIVE'
        ) THEN
            RAISE EXCEPTION 'CHAT messages require an active room membership';
        END IF;
        UPDATE chat_rooms
        SET next_room_seq = next_room_seq + 1, version = version + 1
        WHERE id = NEW.room_id
        RETURNING next_room_seq INTO NEW.room_seq;
        NEW.notification_seq := NULL;
    ELSIF NEW.message_type = 'ADMIN_MESSAGE' THEN
        IF v_sender_role <> 'SYSTEM_ADMIN' AND NOT (
            v_sender_role = 'ROOM_ADMIN' AND EXISTS (
                SELECT 1 FROM admin_room_permissions
                WHERE admin_id = NEW.sender_id AND room_id = NEW.room_id AND revoked_at IS NULL
            )
        ) THEN
            RAISE EXCEPTION 'ADMIN_MESSAGE requires active room administrator permission';
        END IF;
        UPDATE chat_rooms
        SET next_room_seq = next_room_seq + 1, version = version + 1
        WHERE id = NEW.room_id
        RETURNING next_room_seq INTO NEW.room_seq;
        NEW.notification_seq := NULL;
    ELSE
        IF v_sender_role <> 'SYSTEM_ADMIN' AND NOT (
            v_sender_role = 'ROOM_ADMIN' AND EXISTS (
                SELECT 1 FROM admin_room_permissions
                WHERE admin_id = NEW.sender_id AND room_id = NEW.room_id AND revoked_at IS NULL
            )
        ) THEN
            RAISE EXCEPTION 'SYSTEM_NOTIFICATION requires active room administrator permission';
        END IF;
        UPDATE chat_rooms
        SET next_notification_seq = next_notification_seq + 1, version = version + 1
        WHERE id = NEW.room_id
        RETURNING next_notification_seq INTO NEW.notification_seq;
        NEW.room_seq := NULL;
    END IF;
    RETURN NEW;
END;
$$;

-- Advances the single normal-message publication cursor. Call it in the same transaction
-- as a review/timeout result and its audit row. It returns the rows which must be pushed.
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
        SELECT next_publish_seq INTO v_next_seq
        FROM chat_rooms
        WHERE id = p_room_id AND deleted_at IS NULL
        FOR UPDATE;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'room % does not exist or is deleted', p_room_id;
        END IF;

        SELECT id, status INTO v_message_id, v_status
        FROM messages
        WHERE room_id = p_room_id AND room_seq = v_next_seq
        FOR UPDATE;
        IF NOT FOUND THEN
            RETURN;
        END IF;

        IF v_status = 'APPROVED' THEN
            UPDATE messages
            SET status = 'PUBLISHED', published_at = clock_timestamp(), version = version + 1
            WHERE id = v_message_id;
            UPDATE chat_rooms
            SET next_publish_seq = next_publish_seq + 1, version = version + 1
            WHERE id = p_room_id;
            published_message_id := v_message_id;
            published_room_seq := v_next_seq;
            RETURN NEXT;
        ELSIF v_status IN ('REJECTED', 'TIMEOUT', 'CANCELLED_BY_ROOM_DELETION') THEN
            UPDATE chat_rooms
            SET next_publish_seq = next_publish_seq + 1, version = version + 1
            WHERE id = p_room_id;
        ELSE
            -- PENDING_REVIEW blocks a later approved message; PUBLISHED here is an invariant violation.
            RETURN;
        END IF;
    END LOOP;
END;
$$;

CREATE OR REPLACE FUNCTION prevent_audit_log_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs are append-only';
END;
$$;

CREATE OR REPLACE FUNCTION prevent_historical_row_delete()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is historical data and cannot be deleted directly', TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER a_validate_membership_lifecycle
BEFORE INSERT OR UPDATE ON user_chat_rooms
FOR EACH ROW EXECUTE FUNCTION validate_membership_lifecycle();
CREATE TRIGGER a_validate_room_lifecycle
BEFORE INSERT OR UPDATE ON chat_rooms
FOR EACH ROW EXECUTE FUNCTION validate_room_lifecycle();
CREATE TRIGGER a_validate_admin_permission_grantee
BEFORE INSERT ON admin_room_permissions
FOR EACH ROW EXECUTE FUNCTION validate_admin_permission_grantee();
CREATE TRIGGER b_validate_admin_permission_lifecycle
BEFORE UPDATE ON admin_room_permissions
FOR EACH ROW EXECUTE FUNCTION validate_admin_permission_lifecycle();
CREATE TRIGGER z_touch_user_version
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION touch_versioned_row();
CREATE TRIGGER z_touch_room_version
BEFORE UPDATE ON chat_rooms
FOR EACH ROW EXECUTE FUNCTION touch_versioned_row();
CREATE TRIGGER z_touch_membership_version
BEFORE UPDATE ON user_chat_rooms
FOR EACH ROW EXECUTE FUNCTION touch_versioned_row();
CREATE TRIGGER z_touch_admin_permission_version
BEFORE UPDATE ON admin_room_permissions
FOR EACH ROW EXECUTE FUNCTION touch_versioned_row();
CREATE TRIGGER a_assign_message_sequence
BEFORE INSERT ON messages
FOR EACH ROW EXECUTE FUNCTION assign_message_sequence_and_validate_sender();
CREATE TRIGGER b_validate_message_lifecycle
BEFORE INSERT OR UPDATE ON messages
FOR EACH ROW EXECUTE FUNCTION validate_message_lifecycle();
CREATE TRIGGER z_touch_message_version
BEFORE UPDATE ON messages
FOR EACH ROW EXECUTE FUNCTION touch_versioned_row();
CREATE TRIGGER prevent_audit_log_update
BEFORE UPDATE ON audit_logs
FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_mutation();
CREATE TRIGGER prevent_audit_log_delete
BEFORE DELETE ON audit_logs
FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_mutation();
CREATE TRIGGER prevent_membership_delete
BEFORE DELETE ON user_chat_rooms
FOR EACH ROW EXECUTE FUNCTION prevent_historical_row_delete();
CREATE TRIGGER prevent_message_delete
BEFORE DELETE ON messages
FOR EACH ROW EXECUTE FUNCTION prevent_historical_row_delete();
CREATE TRIGGER prevent_admin_permission_delete
BEFORE DELETE ON admin_room_permissions
FOR EACH ROW EXECUTE FUNCTION prevent_historical_row_delete();
CREATE TRIGGER prevent_user_delete
BEFORE DELETE ON users
FOR EACH ROW EXECUTE FUNCTION prevent_historical_row_delete();
CREATE TRIGGER prevent_room_delete
BEFORE DELETE ON chat_rooms
FOR EACH ROW EXECUTE FUNCTION prevent_historical_row_delete();
