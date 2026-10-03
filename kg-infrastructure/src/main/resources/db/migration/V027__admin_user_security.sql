-- Administrative suspension and invalidation of already-issued access tokens.
ALTER TABLE users ADD COLUMN blocked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN tokens_invalid_before TIMESTAMPTZ;
CREATE INDEX idx_users_admin_directory ON users (created_at DESC, id DESC);
