-- Registration codes are separate from password-reset codes and Google OAuth.
CREATE TABLE registration_email_challenges (
    email VARCHAR(320) PRIMARY KEY,
    code_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    consumed BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX registration_email_challenges_expiry ON registration_email_challenges(expires_at);
