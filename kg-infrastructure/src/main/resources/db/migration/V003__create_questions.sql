-- V003 — Questions: questions, question_options (2 tables)
-- tags TEXT[] + GIN (NO question_tags junction), search_vector tsvector, hints JSONB.
-- version INT = optimistic locking (@Version).

CREATE TABLE questions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    module_id       UUID         NOT NULL REFERENCES modules(id),
    title           TEXT         NOT NULL,
    answer_html     TEXT         NOT NULL,
    difficulty      VARCHAR(20)  NOT NULL
                    CHECK (difficulty IN ('JUNIOR', 'MID', 'SENIOR')),
    tags            TEXT[]       NOT NULL DEFAULT '{}',
    search_vector   TSVECTOR,                                  -- ES fallback
    hints           JSONB,                                     -- ordered array, nullable
    sort_order      INT          NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 1,           -- @Version
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_questions_module ON questions (module_id);
CREATE INDEX idx_questions_tags ON questions USING GIN (tags);
CREATE INDEX idx_questions_search ON questions USING GIN (search_vector);

CREATE TABLE question_options (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id     UUID         NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    content         TEXT         NOT NULL,
    is_correct      BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order   INT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_question_options_qid ON question_options (question_id);