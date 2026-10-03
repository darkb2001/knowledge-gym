ALTER TABLE blog_posts DROP CONSTRAINT blog_posts_status_check;
ALTER TABLE blog_posts ADD CONSTRAINT blog_posts_status_check
    CHECK (status IN ('DRAFT','REVIEW','PUBLISHED','ARCHIVED','HIDDEN','DELETED'));

ALTER TABLE blog_comments ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE'
    CHECK (status IN ('VISIBLE','HIDDEN','DELETED'));
ALTER TABLE blog_comments ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE INDEX idx_comments_moderation ON blog_comments (created_at DESC, id DESC);
-- Removal is reversible; public readers must filter status. Keep content and replies for audit.
