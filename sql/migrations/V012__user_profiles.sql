ALTER TABLE users
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(64),
    ADD COLUMN IF NOT EXISTS avatar_url TEXT,
    ADD COLUMN IF NOT EXISTS level INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS bio VARCHAR(240);

UPDATE users
SET display_name = username,
    version = version + 1
WHERE display_name IS NULL OR btrim(display_name) = '';
ALTER TABLE users ALTER COLUMN display_name SET NOT NULL;
ALTER TABLE users ALTER COLUMN display_name SET DEFAULT '';
ALTER TABLE users ADD CONSTRAINT users_level_check CHECK (level >= 1);
