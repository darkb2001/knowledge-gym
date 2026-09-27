-- V011 — Password reset: password_reset_codes (1 table)
-- Tạo ở m02 (m03 chỉ wire use-case, KHÔNG tạo lại migration).
-- TTL 10 phút, lưu SHA-256 của 6-digit code.

CREATE TABLE password_reset_codes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash       VARCHAR(64)  NOT NULL,                     -- SHA-256 of 6-digit code
    attempts        INT          NOT NULL DEFAULT 0,
    used_at         TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ  NOT NULL,                     -- TTL 10m
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prc_user ON password_reset_codes (user_id);
CREATE INDEX idx_prc_expires ON password_reset_codes (expires_at);