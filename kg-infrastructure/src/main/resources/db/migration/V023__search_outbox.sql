-- V023 — Search index outbox: trigger-driven capture of the searchable corpus.
--
-- Deliberately a *separate* table from `event_outbox`. `OutboxKafkaPublisher`
-- claims every unpublished row without filtering on `event_type`
-- (`WHERE published_at IS NULL ... FOR UPDATE SKIP LOCKED`), so putting search
-- events in that table would make each relay steal rows belonging to the other
-- and silently drop events. A dedicated table keeps both relays independent.
--
-- Capture lives in triggers rather than in the repositories so that every write
-- path is covered by construction. There are at least eleven of them
-- (content import upsert + delete-absent, admin question create/update/delete,
-- note insert/update/delete, blog draft/review/revision/restore/publish/archive
-- plus like/view/comment counter updates). Adding an outbox call to each is
-- exactly the kind of thing that gets missed when a new path is added later.
--
-- The row carries no payload: the relay re-reads the source row, so the index
-- always reflects committed state rather than a snapshot taken at write time.

CREATE TABLE search_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_type        VARCHAR(20)  NOT NULL CHECK (doc_type IN ('question', 'note', 'blog')),
    doc_id          UUID         NOT NULL,
    op              VARCHAR(10)  NOT NULL CHECK (op IN ('UPSERT', 'DELETE')),
    -- Ordering key for a source that has no natural one. `occurred_at` is only
    -- second-resolution under NOW(), so batches written in the same tick would
    -- tie; the relay collapses events per document by highest seq instead.
    seq             BIGSERIAL    NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    attempts        INT          NOT NULL DEFAULT 0,
    last_error      TEXT
);

CREATE INDEX idx_search_outbox_pending ON search_outbox (seq) WHERE processed_at IS NULL;

-- ---------------------------------------------------------------------------
-- Capture functions.
--
-- Every function writes both a delete tombstone and an upsert marker as the
-- same row shape, so the relay has one code path.
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION search_outbox_emit() RETURNS trigger AS $$
DECLARE
    v_op    VARCHAR(10);
    v_id    UUID;
    v_type  VARCHAR(20);
BEGIN
    v_type := TG_ARGV[0];
    IF TG_OP = 'DELETE' THEN
        v_op := 'DELETE';
        v_id := OLD.id;
    ELSE
        v_op := 'UPSERT';
        v_id := NEW.id;
    END IF;
    INSERT INTO search_outbox(doc_type, doc_id, op) VALUES (v_type, v_id, v_op);
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- questions: `searchable_text` is the pre-normalised token column produced by
-- SearchText (diacritic-folded, stopwords dropped, n-grams and VI–EN synonyms
-- expanded) and `answer_html` carries the raw answer. Both are indexed, because
-- the searchable_text is what makes Vietnamese queries match at all while the
-- raw text preserves English substring behaviour.
DROP TRIGGER IF EXISTS trg_search_outbox_question ON questions;
CREATE TRIGGER trg_search_outbox_question
    AFTER INSERT OR DELETE OR UPDATE OF title, answer_html, searchable_text ON questions
    FOR EACH ROW EXECUTE FUNCTION search_outbox_emit('question');

-- notes: only the owner can search their notes, so `user_id` is part of the
-- document and the query is always filtered by it. Every column that changes
-- what is searchable must be listed, or an edit leaves a stale index entry.
DROP TRIGGER IF EXISTS trg_search_outbox_note ON notes;
CREATE TRIGGER trg_search_outbox_note
    AFTER INSERT OR DELETE OR UPDATE OF content, note_type, tags, question_id, module_id ON notes
    FOR EACH ROW EXECUTE FUNCTION search_outbox_emit('note');

-- blog_posts: `status` is included because only PUBLISHED posts are searchable;
-- a draft → review → published transition must add the document, and an archive
-- must remove it.
DROP TRIGGER IF EXISTS trg_search_outbox_blog ON blog_posts;
CREATE TRIGGER trg_search_outbox_blog
    AFTER INSERT OR DELETE OR UPDATE OF title, excerpt, body, tags, status ON blog_posts
    FOR EACH ROW EXECUTE FUNCTION search_outbox_emit('blog');

-- ---------------------------------------------------------------------------
-- Backfill.
--
-- Enqueues the current corpus so that flipping the feature flag on an existing
-- database indexes what is already there, instead of only content written after
-- the flag was enabled. Rows sit in the table unprocessed until the relay runs;
-- that is bounded (one row per document) and harmless when search is disabled.
-- ---------------------------------------------------------------------------
INSERT INTO search_outbox(doc_type, doc_id, op)
SELECT 'question', id, 'UPSERT' FROM questions
UNION ALL
SELECT 'note', id, 'UPSERT' FROM notes
UNION ALL
SELECT 'blog', id, 'UPSERT' FROM blog_posts WHERE status = 'PUBLISHED';
