CREATE TABLE room_moderation_settings (
    room_id UUID PRIMARY KEY REFERENCES chat_rooms(id),
    enabled BOOLEAN NOT NULL,
    updated_by UUID NOT NULL REFERENCES users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
