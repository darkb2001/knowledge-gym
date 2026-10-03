CREATE TABLE learning_content_drafts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id UUID REFERENCES knowledge_goals(id) ON DELETE SET NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('QUESTION','FLASHCARD','INTERVIEW')),
    title TEXT NOT NULL,
    payload JSONB NOT NULL,
    source_ids UUID[] NOT NULL DEFAULT '{}',
    status VARCHAR(20) NOT NULL DEFAULT 'REVIEW' CHECK (status IN ('REVIEW','APPROVED','REJECTED')),
    reviewer_id UUID REFERENCES users(id) ON DELETE SET NULL,
    review_reason TEXT,
    model VARCHAR(100),
    tokens_used INT NOT NULL DEFAULT 0,
    cost_usd NUMERIC(10,6) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMPTZ
);
CREATE INDEX idx_learning_drafts_review ON learning_content_drafts(status, created_at DESC);
CREATE INDEX idx_learning_drafts_goal ON learning_content_drafts(goal_id, created_at DESC);

CREATE TABLE learning_draft_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    draft_id UUID NOT NULL REFERENCES learning_content_drafts(id) ON DELETE CASCADE,
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(30) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
