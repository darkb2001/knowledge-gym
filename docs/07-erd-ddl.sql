-- Knowledge Gym — Flyway-ready DDL reference (V001–V013)
-- Source of truth for columns/types/CHECKs. Split into Flyway files in m02.
-- Canonical OAuth columns: auth_provider + oauth_id (NO oauth_provider).
-- Enums: UPPERCASE. IDs: UUID. Timestamps: TIMESTAMPTZ.

-- =============================================================================
-- V001 — Identity (core)
-- =============================================================================
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               VARCHAR(320) NOT NULL,
    password_hash       VARCHAR(100),                          -- NULL when OAuth-only
    display_name        VARCHAR(100) NOT NULL,
    avatar_url          TEXT,
    role                VARCHAR(20)  NOT NULL DEFAULT 'USER'
                        CHECK (role IN ('USER', 'ADMIN', 'PREMIUM')),  -- PREMIUM reserved
    auth_provider       VARCHAR(20)  NOT NULL DEFAULT 'LOCAL'
                        CHECK (auth_provider IN ('LOCAL', 'GOOGLE', 'GITHUB')),  -- GITHUB reserved
    oauth_id            VARCHAR(255),                          -- NULL for LOCAL
    email_verified      BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verified_at   TIMESTAMPTZ,
    xp                  INT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE UNIQUE INDEX idx_users_oauth
    ON users (auth_provider, oauth_id)
    WHERE oauth_id IS NOT NULL;

CREATE INDEX idx_users_xp ON users (xp DESC);

CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(64)  NOT NULL,                     -- SHA-256 hex
    family_id       UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ  NOT NULL,                     -- TTL 7d
    revoked_at      TIMESTAMPTZ,
    replaced_by     UUID,                                      -- next token in rotation
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user
    ON refresh_tokens (user_id)
    WHERE revoked_at IS NULL;

CREATE TABLE notification_preferences (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel         VARCHAR(20)  NOT NULL
                    CHECK (channel IN ('EMAIL', 'PUSH')),
    frequency       VARCHAR(20)  NOT NULL DEFAULT 'DAILY'
                    CHECK (frequency IN ('DAILY', 'WEEKLY')),
    quiet_hours     JSONB,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_notif_pref_user_channel UNIQUE (user_id, channel)
);

-- =============================================================================
-- V002 — Content hierarchy
-- =============================================================================
CREATE TABLE topics (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200) NOT NULL,
    slug            VARCHAR(200) NOT NULL,
    description     TEXT,
    icon            VARCHAR(64),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_topics_slug UNIQUE (slug)
);

CREATE TABLE modules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id        UUID         NOT NULL REFERENCES topics(id),
    name            VARCHAR(200) NOT NULL,
    slug            VARCHAR(200) NOT NULL,
    description     TEXT,
    display_order   INT          NOT NULL DEFAULT 0,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_modules_slug UNIQUE (slug)
);

CREATE INDEX idx_modules_topic ON modules (topic_id);

-- =============================================================================
-- V003 — Questions
-- =============================================================================
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

-- =============================================================================
-- V004 — SRS + progress
-- =============================================================================
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
    ease_factor          NUMERIC(4, 2)  NOT NULL DEFAULT 2.50,
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

-- =============================================================================
-- V005 — Notes
-- =============================================================================
CREATE TABLE notes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_id         UUID         REFERENCES questions(id) ON DELETE SET NULL,
    module_id           UUID         REFERENCES modules(id) ON DELETE SET NULL,
    note_type           VARCHAR(20)  NOT NULL
                        CHECK (note_type IN ('QUICK', 'STUDY', 'HIGHLIGHT', 'BOOKMARK')),
    content             TEXT,
    highlight_range     JSONB,
    tags                TEXT[]       NOT NULL DEFAULT '{}',
    search_vector       TSVECTOR,
    is_public           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notes_user ON notes (user_id);
CREATE INDEX idx_notes_search ON notes USING GIN (search_vector);
CREATE INDEX idx_notes_tags ON notes USING GIN (tags);

-- Backfill soft FK from srs_cards (run in V005 after notes exists)
ALTER TABLE srs_cards
    ADD CONSTRAINT fk_srs_cards_source_note
    FOREIGN KEY (source_note_id) REFERENCES notes(id) ON DELETE SET NULL;

-- =============================================================================
-- V006 — Blog social
-- =============================================================================
CREATE TABLE blog_posts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id           UUID         REFERENCES users(id) ON DELETE SET NULL,  -- NULL when AI
    author_type         VARCHAR(20)  NOT NULL
                        CHECK (author_type IN ('AI', 'HUMAN')),
    title               TEXT         NOT NULL,
    slug                VARCHAR(300) NOT NULL,
    body                TEXT         NOT NULL,
    excerpt             TEXT,
    cover_image_url     TEXT,                                                  -- Garage
    source_module_id    UUID         REFERENCES modules(id) ON DELETE SET NULL,
    source_question_id  UUID         REFERENCES questions(id) ON DELETE SET NULL,
    source_note_id      UUID         REFERENCES notes(id) ON DELETE SET NULL,
    quality_score       NUMERIC(5, 2),
    seo_title           TEXT,
    seo_description     TEXT,
    seo_keywords        TEXT[],
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT', 'REVIEW', 'PUBLISHED', 'ARCHIVED')),
    published_at        TIMESTAMPTZ,
    view_count          INT          NOT NULL DEFAULT 0,                       -- cache
    like_count          INT          NOT NULL DEFAULT 0,                       -- cache
    tags                TEXT[]       NOT NULL DEFAULT '{}',
    ai_model            VARCHAR(100),
    ai_prompt_used      TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_blog_posts_slug UNIQUE (slug)
);

CREATE INDEX idx_blog_published ON blog_posts (published_at DESC) WHERE status = 'PUBLISHED';

CREATE TABLE blog_comments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID         NOT NULL REFERENCES blog_posts(id) ON DELETE CASCADE,
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    parent_id       UUID         REFERENCES blog_comments(id) ON DELETE CASCADE,
    content         TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE blog_views (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID         NOT NULL REFERENCES blog_posts(id) ON DELETE CASCADE,
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    viewed_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    read_time_sec   INT,
    CONSTRAINT uk_blog_views_post_user UNIQUE (post_id, user_id)
);

CREATE TABLE blog_post_likes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID         NOT NULL REFERENCES blog_posts(id) ON DELETE CASCADE,
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_blog_likes_post_user UNIQUE (post_id, user_id)
);

-- =============================================================================
-- V007 — Collector / agent
-- =============================================================================
CREATE TABLE collector_sources (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    type                VARCHAR(20)  NOT NULL
                        CHECK (type IN ('RSS', 'API', 'SCRAPE')),
    url                 TEXT         NOT NULL,
    config              JSONB,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    last_fetched_at     TIMESTAMPTZ,
    fetch_interval_sec  INT          NOT NULL DEFAULT 21600
);

CREATE TABLE collected_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id           UUID           NOT NULL REFERENCES collector_sources(id),
    title               TEXT           NOT NULL,
    url                 TEXT           NOT NULL,
    summary             TEXT,
    content_hash        VARCHAR(64),
    score               NUMERIC(5, 2),
    category            VARCHAR(100),
    tags                TEXT[],
    published_at        TIMESTAMPTZ,
    collected_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    used_in_post_id     UUID           REFERENCES blog_posts(id) ON DELETE SET NULL,
    CONSTRAINT uk_collected_items_url UNIQUE (url)
);

CREATE TABLE blog_generation_queue (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic                   VARCHAR(200) NOT NULL,
    angle                   TEXT,
    priority                INT          NOT NULL DEFAULT 0,
    selection_strategy      VARCHAR(20)  NOT NULL
                            CHECK (selection_strategy IN ('TRENDING', 'GAP', 'SEASONAL', 'DEMAND')),
    writer_strategy         VARCHAR(20)  NOT NULL
                            CHECK (writer_strategy IN ('AUTO', 'TEMPLATE', 'CURATED')),
    reference_items         JSONB,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'QUEUED'
                            CHECK (status IN ('QUEUED', 'GENERATING', 'DONE', 'FAILED')),
    scheduled_for           TIMESTAMPTZ,
    error_message           TEXT
);

CREATE TABLE agent_runs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_type          VARCHAR(20)  NOT NULL
                        CHECK (agent_type IN ('COLLECTOR', 'WRITER', 'PUBLISHER')),
    queue_id            UUID         REFERENCES blog_generation_queue(id) ON DELETE SET NULL,
    status              VARCHAR(20)  NOT NULL
                        CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    started_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    finished_at         TIMESTAMPTZ,
    input_summary       TEXT,
    output_summary      TEXT,
    error_message       TEXT,
    tokens_used         INT,
    cost_usd            NUMERIC(10, 6)
);

-- =============================================================================
-- V008 — Audit + badges
-- =============================================================================
CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         REFERENCES users(id) ON DELETE SET NULL,
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(100),
    entity_id       UUID,
    ip_address      VARCHAR(45),
    details         JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user ON audit_logs (user_id, created_at DESC);

CREATE TABLE user_badges (
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    badge_code      VARCHAR(50)  NOT NULL,                     -- ENUM string, no lookup table
    earned_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, badge_code)
);

-- =============================================================================
-- V009 — Extra composite/partial indexes (already inlined above where critical)
-- Keep empty or add remaining composites in Flyway file.
-- =============================================================================

-- =============================================================================
-- V010 — Materialized view
-- =============================================================================
CREATE MATERIALIZED VIEW user_topic_mastery AS
SELECT
    up.user_id,
    t.id AS topic_id,
    AVG(up.mastery_pct) AS mastery_pct,
    SUM(up.total_attempts) AS total_attempts
FROM user_progress up
JOIN modules m ON m.id = up.module_id
JOIN topics t ON t.id = m.topic_id
GROUP BY up.user_id, t.id;

CREATE UNIQUE INDEX idx_utm_user_topic ON user_topic_mastery (user_id, topic_id);

-- =============================================================================
-- V011 — Password reset
-- =============================================================================
CREATE TABLE password_reset_codes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash       VARCHAR(64)  NOT NULL,                     -- SHA-256 of 6-digit code
    attempts        INT          NOT NULL DEFAULT 0,
    used_at         TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ  NOT NULL,                     -- TTL 10m
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prc_user ON password_reset_codes (user_id);
CREATE INDEX idx_prc_expires ON password_reset_codes (expires_at);

-- =============================================================================
-- V012 — Code challenges
-- =============================================================================
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

-- =============================================================================
-- V013 — Quiz / interview / notifications / daily
-- =============================================================================
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
    started_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    finished_at         TIMESTAMPTZ
);

CREATE TABLE interview_answers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID           NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
    question_id         UUID           NOT NULL REFERENCES questions(id),
    user_answer         TEXT,                                                  -- NULL = placeholder membership
    display_order       INT            NOT NULL,
    audio_url           TEXT,                                                  -- Garage
    answer_html         TEXT,                                                  -- đáp án mẫu trả cho user (V035)
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
