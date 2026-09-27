-- V005 — Notes: notes (1 table) + backfill FK source_note_id → notes

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

ALTER TABLE srs_cards
    ADD CONSTRAINT fk_srs_cards_source_note
    FOREIGN KEY (source_note_id) REFERENCES notes(id) ON DELETE SET NULL;