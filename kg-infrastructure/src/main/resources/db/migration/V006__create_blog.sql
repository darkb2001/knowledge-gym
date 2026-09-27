-- V006 — Blog social: blog_posts, blog_comments, blog_views, blog_post_likes (4 tables)
-- blog_views + blog_post_likes đều có UK (post_id, user_id).

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