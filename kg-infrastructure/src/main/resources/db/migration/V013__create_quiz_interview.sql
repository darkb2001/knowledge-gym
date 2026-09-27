-- V013 — Quiz / interview / notifications / daily (6 tables)
-- FK ordering: tham chiếu questions (V003) + topics (V002) + question_options (V003).
-- daily_challenge_assignments: UK (user_id, challenge_date).

CREATE TABLE quiz_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    strategy        VARCHAR(20)  NOT NULL
                    CHECK (strategy IN ('RANDOM', 'WEAKNESS', 'INTERVIEW', 'SPACED')),
    score           INT,
    total           INT,
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    finished_at     TIMESTAMPTZ
);

CREATE TABLE quiz_answers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID         NOT NULL REFERENCES quiz_sessions(id) ON DELETE CASCADE,
    question_id         UUID         NOT NULL REFERENCES questions(id),
    selected_option_id  UUID         REFERENCES question_options(id) ON DELETE SET NULL,
    answer_text         TEXT,
    is_correct          BOOLEAN,
    time_ms             INT
);

CREATE TABLE interview_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic_id            UUID           NOT NULL REFERENCES topics(id),
    question_count      INT            NOT NULL,
    mode                VARCHAR(20)    NOT NULL
                        CHECK (mode IN ('TEXT', 'AUDIO')),
    status              VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE', 'FINISHED')),
    overall_score       NUMERIC(5, 2),
    started_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    finished_at         TIMESTAMPTZ
);

CREATE TABLE interview_answers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID           NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
    question_id         UUID           NOT NULL REFERENCES questions(id),
    user_answer         TEXT,
    audio_url           TEXT,                                                  -- Garage
    keyword_score       NUMERIC(5, 2),
    feedback            TEXT,
    sample_answer       TEXT,
    attempted_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            VARCHAR(40)  NOT NULL
                    CHECK (type IN ('REVIEW_DUE', 'DAILY_CHALLENGE', 'SYSTEM')),
    title           TEXT         NOT NULL,
    body            TEXT,
    metadata        JSONB,
    read            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notif_unread ON notifications (user_id, created_at DESC) WHERE read = FALSE;

CREATE TABLE daily_challenge_assignments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_id         UUID         NOT NULL REFERENCES questions(id),
    challenge_date      DATE         NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'COMPLETED', 'SKIPPED')),
    completed_at        TIMESTAMPTZ,
    CONSTRAINT uk_daily_user_date UNIQUE (user_id, challenge_date)
);

CREATE INDEX idx_daily_user_date ON daily_challenge_assignments (user_id, challenge_date DESC);