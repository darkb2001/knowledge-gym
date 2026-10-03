CREATE TABLE knowledge_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    objective TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACTIVE','PAUSED','ARCHIVED')),
    auto_publish BOOLEAN NOT NULL DEFAULT FALSE,
    daily_item_limit INT NOT NULL DEFAULT 20 CHECK (daily_item_limit BETWEEN 1 AND 200),
    daily_cost_limit_usd NUMERIC(10,2) NOT NULL DEFAULT 10 CHECK (daily_cost_limit_usd >= 0),
    schedule_cron VARCHAR(100),
    allowed_domains TEXT[] NOT NULL DEFAULT '{}',
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_knowledge_goals_status ON knowledge_goals(status);

CREATE TABLE knowledge_intake_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id UUID NOT NULL REFERENCES knowledge_goals(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','RUNNING','REVIEW','PUBLISHED','FAILED','CANCELLED')),
    requested_by UUID REFERENCES users(id) ON DELETE SET NULL,
    discovered_count INT NOT NULL DEFAULT 0,
    accepted_count INT NOT NULL DEFAULT 0,
    rejected_count INT NOT NULL DEFAULT 0,
    cost_usd NUMERIC(10,6) NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_knowledge_intake_runs_goal ON knowledge_intake_runs(goal_id, created_at DESC);

CREATE TABLE knowledge_intake_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id UUID NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(40) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
