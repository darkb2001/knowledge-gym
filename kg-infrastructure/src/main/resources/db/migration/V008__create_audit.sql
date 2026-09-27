-- V008 — Audit + badges: audit_logs, user_badges (2 tables)
-- user_badges: composite PK (user_id, badge_code) — ENUM string, không có lookup table.

CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         REFERENCES users(id) ON DELETE SET NULL,
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(100),
    entity_id       UUID,
    ip_address      VARCHAR(45),
    details         JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user ON audit_logs (user_id, created_at DESC);

CREATE TABLE user_badges (
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    badge_code      VARCHAR(50)  NOT NULL,                     -- ENUM string, no lookup table
    earned_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, badge_code)
);