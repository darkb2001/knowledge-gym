-- V012 — Code challenges: challenges, challenge_test_cases, code_submissions (3 tables)
-- code_submissions.challenge_id FK → challenges (KHÔNG PHẢI question_id).

CREATE TABLE challenges (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug                VARCHAR(200) NOT NULL,
    title               VARCHAR(300) NOT NULL,
    prompt              TEXT         NOT NULL,
    starter_code        TEXT,
    solution_code       TEXT,
    difficulty          VARCHAR(20)  NOT NULL
                        CHECK (difficulty IN ('JUNIOR', 'MID', 'SENIOR')),
    tags                TEXT[]       NOT NULL DEFAULT '{}',
    hints               JSONB,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_challenges_slug UNIQUE (slug)
);

CREATE TABLE challenge_test_cases (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    challenge_id        UUID         NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
    input               TEXT         NOT NULL,
    expected_output     TEXT         NOT NULL,
    is_hidden           BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order       INT          NOT NULL DEFAULT 0
);

CREATE TABLE code_submissions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    challenge_id        UUID         NOT NULL REFERENCES challenges(id),  -- NOT question_id
    source_code         TEXT         NOT NULL,
    language            VARCHAR(40)  NOT NULL DEFAULT 'java',
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'RUNNING', 'PASS', 'FAIL')),
    passed_count        INT,
    total_count         INT,
    stdout              TEXT,
    stderr              TEXT,
    runtime_ms          INT,
    memory_mb           INT,
    viewed_solution_at  TIMESTAMPTZ,
    submitted_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_code_sub_user ON code_submissions (user_id, submitted_at DESC);