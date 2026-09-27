-- V004 — SRS + progress: srs_decks, srs_cards, study_attempts, user_progress (4 tables)
-- srs_cards: UK (user_id, question_id), deck_id + source_note_id nullable
--   (FK source_note_id → notes added in V005).
-- user_progress: UK (user_id, module_id).

CREATE TABLE srs_decks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    module_id       UUID         REFERENCES modules(id) ON DELETE SET NULL,  -- NULL = custom
    is_custom       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_srs_decks_user ON srs_decks (user_id);

CREATE TABLE srs_cards (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_id         UUID           NOT NULL REFERENCES questions(id),
    deck_id             UUID           REFERENCES srs_decks(id) ON DELETE SET NULL,
    source_note_id      UUID,                                              -- FK notes added after V005; soft until then
    interval_days       INT            NOT NULL DEFAULT 0,
    ease_factor         NUMERIC(4, 2)  NOT NULL DEFAULT 2.50,
    next_review         DATE           NOT NULL DEFAULT CURRENT_DATE,
    repetitions         INT            NOT NULL DEFAULT 0,
    last_reviewed_at    TIMESTAMPTZ,
    CONSTRAINT uk_srs_cards_user_question UNIQUE (user_id, question_id)
);

CREATE INDEX idx_srs_due ON srs_cards (user_id, next_review);

CREATE TABLE study_attempts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_id     UUID           NOT NULL REFERENCES questions(id),
    source          VARCHAR(20)    NOT NULL
                    CHECK (source IN ('FLASHCARD', 'DAILY', 'PRACTICE')),
    answer          TEXT,
    is_correct      BOOLEAN        NOT NULL,
    score           NUMERIC(5, 2),
    time_ms         INT,
    attempted_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_attempts_user_date ON study_attempts (user_id, attempted_at DESC);
CREATE INDEX idx_attempts_wrong ON study_attempts (question_id) WHERE is_correct = FALSE;

CREATE TABLE user_progress (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    module_id       UUID           NOT NULL REFERENCES modules(id),
    mastery_pct     NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    total_attempts  INT            NOT NULL DEFAULT 0,
    correct_count   INT            NOT NULL DEFAULT 0,
    streak_days     INT            NOT NULL DEFAULT 0,
    last_active_at  TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_progress_user_module UNIQUE (user_id, module_id)
);