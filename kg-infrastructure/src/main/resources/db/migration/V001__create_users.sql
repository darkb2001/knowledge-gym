-- V001 — Identity (core): users, refresh_tokens, notification_preferences (3 tables)

CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               VARCHAR(320) NOT NULL,
    password_hash       VARCHAR(100),
    display_name        VARCHAR(100) NOT NULL,
    avatar_url          TEXT,
    role                VARCHAR(20)  NOT NULL DEFAULT 'USER'
                        CHECK (role IN ('USER', 'ADMIN', 'PREMIUM')),
    auth_provider       VARCHAR(20)  NOT NULL DEFAULT 'LOCAL'
                        CHECK (auth_provider IN ('LOCAL', 'GOOGLE', 'GITHUB')),
    oauth_id            VARCHAR(255),
    email_verified      BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verified_at   TIMESTAMPTZ,
    xp                  INT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE UNIQUE INDEX idx_users_oauth
    ON users (auth_provider, oauth_id)
    WHERE oauth_id IS NOT NULL;

CREATE INDEX idx_users_xp ON users (xp DESC);

CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(64)  NOT NULL,
    family_id       UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ,
    replaced_by     UUID,
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user
    ON refresh_tokens (user_id)
    WHERE revoked_at IS NULL;

CREATE TABLE notification_preferences (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel         VARCHAR(20)  NOT NULL
                    CHECK (channel IN ('EMAIL', 'PUSH')),
    frequency       VARCHAR(20)  NOT NULL DEFAULT 'DAILY'
                    CHECK (frequency IN ('DAILY', 'WEEKLY')),
    quiet_hours     JSONB,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_notif_pref_user_channel UNIQUE (user_id, channel)
);