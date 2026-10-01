-- M9: transactional event outbox, published-blog full-text search, and safe starter sources.
CREATE TABLE event_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    attempts        INT NOT NULL DEFAULT 0,
    last_error      TEXT
);
CREATE INDEX idx_event_outbox_pending ON event_outbox (occurred_at) WHERE published_at IS NULL;

ALTER TABLE blog_posts ADD COLUMN search_vector TSVECTOR;
CREATE INDEX idx_blog_posts_search ON blog_posts USING GIN (search_vector);

CREATE OR REPLACE FUNCTION blog_posts_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple',
        coalesce(NEW.title, '') || ' ' || coalesce(NEW.excerpt, '') || ' ' ||
        coalesce(NEW.body, '') || ' ' || coalesce(array_to_string(NEW.tags, ' '), ''));
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_blog_posts_search_vector
    BEFORE INSERT OR UPDATE OF title, excerpt, body, tags ON blog_posts
    FOR EACH ROW EXECUTE FUNCTION blog_posts_search_vector_update();
UPDATE blog_posts SET title = title WHERE search_vector IS NULL;

CREATE UNIQUE INDEX uk_collected_items_content_hash
    ON collected_items(content_hash) WHERE content_hash IS NOT NULL;

-- Seed sources need a conflict target; url is the natural unique key for a feed endpoint.
CREATE UNIQUE INDEX uk_collector_sources_url ON collector_sources(url);

INSERT INTO collector_sources(name, type, url, config, active, fetch_interval_sec)
VALUES
    ('Baeldung Java RSS', 'RSS', 'https://www.baeldung.com/feed', '{"category":"java"}'::jsonb, TRUE, 21600),
    ('Spring Boot Releases', 'API', 'https://api.github.com/repos/spring-projects/spring-boot/releases?per_page=20', '{"category":"spring"}'::jsonb, TRUE, 21600)
ON CONFLICT (url) DO NOTHING;
