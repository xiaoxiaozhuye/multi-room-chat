CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(64) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash TEXT NOT NULL,
    role user_role NOT NULL DEFAULT 'USER',
    status user_status NOT NULL DEFAULT 'ACTIVE',
    version INTEGER NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    deleted_at TIMESTAMPTZ,
    CHECK (username = btrim(username) AND char_length(username) BETWEEN 3 AND 64),
    CHECK (email = lower(btrim(email)) AND position('@' IN email) > 1),
    CHECK (deleted_at IS NULL OR deleted_at >= created_at)
);

CREATE TABLE chat_rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    description TEXT,
    max_members INTEGER NOT NULL DEFAULT 100 CHECK (max_members > 0 AND max_members <= 100000),
    join_mode room_join_mode NOT NULL DEFAULT 'OPEN',
    status room_status NOT NULL DEFAULT 'ACTIVE',
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    -- These counters are changed only by the sequence/publish functions in V003.
    next_room_seq BIGINT NOT NULL DEFAULT 0 CHECK (next_room_seq >= 0),
    next_notification_seq BIGINT NOT NULL DEFAULT 0 CHECK (next_notification_seq >= 0),
    next_publish_seq BIGINT NOT NULL DEFAULT 1 CHECK (next_publish_seq >= 1),
    version INTEGER NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    deleted_at TIMESTAMPTZ,
    CHECK (name = btrim(name) AND char_length(name) BETWEEN 1 AND 120),
    CHECK (deleted_at IS NULL OR deleted_at >= created_at)
);

CREATE TABLE user_chat_rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE RESTRICT,
    status membership_status NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    activated_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    exited_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (
        (status = 'PENDING' AND activated_at IS NULL AND resolved_at IS NULL AND resolved_by IS NULL AND exited_at IS NULL)
        OR (status = 'ACTIVE' AND activated_at IS NOT NULL AND exited_at IS NULL)
        OR (status = 'REJECTED' AND activated_at IS NULL AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL AND exited_at IS NULL)
        OR (status = 'EXITED' AND activated_at IS NOT NULL AND exited_at IS NOT NULL)
    ),
    CHECK (activated_at IS NULL OR activated_at >= requested_at),
    CHECK (resolved_at IS NULL OR resolved_at >= requested_at),
    CHECK (exited_at IS NULL OR exited_at >= requested_at)
);

CREATE TABLE admin_room_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE RESTRICT,
    granted_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    revoked_at TIMESTAMPTZ,
    revoked_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    version INTEGER NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK ((revoked_at IS NULL AND revoked_by IS NULL) OR (revoked_at IS NOT NULL AND revoked_by IS NOT NULL)),
    CHECK (revoked_at IS NULL OR revoked_at >= granted_at)
);

CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- The client must reuse request_id for every retry of the same send action.
    request_id UUID NOT NULL,
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE RESTRICT,
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    room_seq BIGINT,
    notification_seq BIGINT,
    message_type message_type NOT NULL,
    content TEXT NOT NULL,
    status message_status NOT NULL,
    review_deadline_at TIMESTAMPTZ,
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    published_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (char_length(content) BETWEEN 1 AND 320 AND btrim(content) <> ''),
    CHECK (review_deadline_at IS NULL OR review_deadline_at > created_at),
    CHECK (
        (message_type IN ('CHAT', 'ADMIN_MESSAGE') AND room_seq IS NOT NULL AND notification_seq IS NULL)
        OR (message_type = 'SYSTEM_NOTIFICATION' AND room_seq IS NULL AND notification_seq IS NOT NULL)
    ),
    CHECK (
        (message_type = 'CHAT' AND review_deadline_at IS NOT NULL)
        OR (message_type IN ('ADMIN_MESSAGE', 'SYSTEM_NOTIFICATION') AND review_deadline_at IS NULL)
    ),
    CHECK (message_type <> 'ADMIN_MESSAGE' OR status IN ('APPROVED', 'PUBLISHED')),
    CHECK (message_type <> 'SYSTEM_NOTIFICATION' OR (status = 'PUBLISHED' AND published_at IS NOT NULL)),
    CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL),
    CHECK (
        NOT (message_type = 'CHAT' AND status IN ('APPROVED', 'REJECTED'))
        OR (reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL)
    )
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID,
    actor_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    action audit_action NOT NULL,
    resource_type audit_resource_type NOT NULL,
    resource_id UUID NOT NULL,
    room_id UUID REFERENCES chat_rooms(id) ON DELETE RESTRICT,
    message_id UUID REFERENCES messages(id) ON DELETE RESTRICT,
    membership_id UUID REFERENCES user_chat_rooms(id) ON DELETE RESTRICT,
    before_state JSONB,
    after_state JSONB,
    detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (jsonb_typeof(detail) = 'object')
);
