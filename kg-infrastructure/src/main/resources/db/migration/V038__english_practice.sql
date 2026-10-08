-- Original VSTEP-aligned mini-practice, isolated from IT question/SRS/XP history.
CREATE TABLE english_attempts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    exercise_id VARCHAR(100) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SUBMITTED')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    answers JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(answers) = 'object'),
    response TEXT NOT NULL DEFAULT '' CHECK (length(response) <= 20000),
    elapsed_seconds INTEGER NOT NULL DEFAULT 0 CHECK (elapsed_seconds BETWEEN 0 AND 7200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX english_one_draft_per_exercise ON english_attempts(user_id, exercise_id) WHERE status='DRAFT';
CREATE INDEX english_attempts_owner_history ON english_attempts(user_id, updated_at DESC, id);
