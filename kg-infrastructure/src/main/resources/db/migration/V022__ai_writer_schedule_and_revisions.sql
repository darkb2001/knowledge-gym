-- M10: persistent AI writer policy/schedule, idempotent queue and immutable draft versions.
CREATE TABLE blog_writer_settings (
    id                  BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (id),
    schedule_enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    local_time          TIME NOT NULL DEFAULT '06:00',
    timezone            VARCHAR(80) NOT NULL DEFAULT 'Asia/Jakarta',
    daily_limit         INT NOT NULL DEFAULT 1 CHECK (daily_limit BETWEEN 1 AND 10),
    publish_policy      VARCHAR(32) NOT NULL DEFAULT 'MANUAL_REVIEW'
                        CHECK (publish_policy IN ('MANUAL_REVIEW','AUTO_PUBLISH_QUALIFIED')),
    quality_threshold   INT NOT NULL DEFAULT 85 CHECK (quality_threshold BETWEEN 0 AND 100),
    last_scheduled_date DATE,
    last_run_at         TIMESTAMPTZ,
    updated_by          UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
INSERT INTO blog_writer_settings(id) VALUES(TRUE);

ALTER TABLE blog_generation_queue
    ADD COLUMN idempotency_key VARCHAR(160),
    ADD COLUMN requested_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN retry_after TIMESTAMPTZ,
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN completed_at TIMESTAMPTZ;
ALTER TABLE blog_generation_queue ADD COLUMN generated_post_id UUID REFERENCES blog_posts(id) ON DELETE SET NULL;
CREATE UNIQUE INDEX uk_blog_generation_idempotency ON blog_generation_queue(idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_blog_generation_ready ON blog_generation_queue(status, scheduled_for, retry_after);

CREATE TABLE blog_draft_revisions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID NOT NULL REFERENCES blog_posts(id) ON DELETE CASCADE,
    version         INT NOT NULL CHECK (version > 0),
    title           TEXT NOT NULL,
    body            TEXT NOT NULL,
    excerpt         TEXT,
    seo_title       TEXT,
    seo_description TEXT,
    seo_keywords    TEXT[],
    source_ids      UUID[] NOT NULL DEFAULT '{}',
    instruction     TEXT,
    model           VARCHAR(120),
    quality_score   INT CHECK (quality_score BETWEEN 0 AND 100),
    tokens_used     INT,
    cost_usd        NUMERIC(10,6),
    created_by      UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_blog_draft_revision UNIQUE(post_id, version)
);

CREATE TABLE blog_writer_audit (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id        UUID REFERENCES users(id) ON DELETE SET NULL,
    action          VARCHAR(40) NOT NULL,
    old_policy      VARCHAR(32),
    new_policy      VARCHAR(32),
    details         JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE blog_ai_budget_reservations (
    request_id      UUID PRIMARY KEY,
    usage_date      DATE NOT NULL DEFAULT CURRENT_DATE,
    tokens_used     INT NOT NULL,
    cost_usd        NUMERIC(10,6) NOT NULL,
    finalized       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_blog_ai_budget_day ON blog_ai_budget_reservations(usage_date);
