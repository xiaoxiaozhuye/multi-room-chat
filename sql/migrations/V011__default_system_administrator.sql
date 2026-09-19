-- Development bootstrap account. Change this password before any non-development deployment.
-- BCrypt hash for the explicitly configured default password: Admin123!
INSERT INTO users (id, username, email, password_hash, role, status)
VALUES (
    '00000000-0000-0000-0000-000000000004',
    'admin',
    'admin@multichat.local',
    '$2a$10$OkHZCKyZ2s/o1Uvvkm3Y9.A3zjc6GcO6uCCBQCvPcXL75yaxOsR0q',
    'SYSTEM_ADMIN',
    'ACTIVE'
)
ON CONFLICT (lower(username)) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    role = EXCLUDED.role,
    status = EXCLUDED.status,
    deleted_at = NULL,
    version = users.version + 1;
