CREATE TABLE learning_draft_materializations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    draft_id UUID NOT NULL REFERENCES learning_content_drafts(id) ON DELETE RESTRICT,
    module_id UUID NOT NULL REFERENCES modules(id) ON DELETE RESTRICT,
    question_id UUID NOT NULL REFERENCES questions(id) ON DELETE RESTRICT,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('QUESTION','FLASHCARD','INTERVIEW')),
    content_hash CHAR(64) NOT NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_learning_materialized_draft UNIQUE (draft_id),
    CONSTRAINT uk_learning_materialized_content UNIQUE (module_id, content_hash)
);
CREATE INDEX idx_learning_materialized_question ON learning_draft_materializations(question_id);
