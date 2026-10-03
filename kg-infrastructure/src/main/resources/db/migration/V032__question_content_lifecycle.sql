ALTER TABLE questions ADD COLUMN content_status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED';
ALTER TABLE questions ADD CONSTRAINT questions_content_status_check CHECK (content_status IN ('DRAFT','PUBLISHED','HIDDEN','ARCHIVED'));
CREATE INDEX idx_questions_content_status ON questions(content_status);
