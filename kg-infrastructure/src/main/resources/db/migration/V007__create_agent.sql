-- V007 — Collector / agent: collector_sources, collected_items, blog_generation_queue, agent_runs (4 tables)

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