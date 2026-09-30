-- V015 — Full-text search cho questions (m4)
--
-- `questions.search_vector TSVECTOR` + GIN index đã có từ V003 nhưng chưa bao giờ được
-- populate → `idx_questions_search` vô dụng. Migration này làm nó hoạt động thật.
--
-- Thiết kế:
-- * `searchable_text` = token do người/AI gán (synonym tiếng Việt, viết tắt như "gc", "jvm").
--   Tokenize động từ title/answer KHÔNG đủ: người học gõ "sao lưu" phải match câu chỉ chứa
--   "replication"/"backup", gõ "khong dong bo" phải match "bất đồng bộ".
-- * Trigger là **đường ghi duy nhất** cho `search_vector`: nó fire cả khi INSERT và khi
--   UPDATE OF title/answer_html/searchable_text, nên không có đường nào ghi lệch.
-- * Không dùng generated column được: `to_tsvector` không phải IMMUTABLE (phụ thuộc text
--   search config) và `array_to_string` cũng không immutable trong PostgreSQL.
-- * Config 'simple': nội dung trộn tiếng Việt + tiếng Anh; stemming tiếng Anh sẽ băm nát
--   từ tiếng Việt. 'simple' chỉ lowercase + tokenize.

ALTER TABLE questions ADD COLUMN searchable_text TEXT NOT NULL DEFAULT '';

CREATE OR REPLACE FUNCTION questions_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple',
        coalesce(NEW.title, '') || ' ' ||
        coalesce(NEW.answer_html, '') || ' ' ||
        coalesce(NEW.searchable_text, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_questions_search ON questions;
CREATE TRIGGER trg_questions_search
    BEFORE INSERT OR UPDATE OF title, answer_html, searchable_text ON questions
    FOR EACH ROW EXECUTE FUNCTION questions_search_vector_update();

-- Backfill (hiện 0 rows). `title = title` là no-op nhưng nằm trong UPDATE OF title nên trigger fire.
UPDATE questions SET title = title WHERE search_vector IS NULL;
