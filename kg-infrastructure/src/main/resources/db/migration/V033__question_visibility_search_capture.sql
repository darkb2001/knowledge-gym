-- Publish/hide must update the external index as well as the PostgreSQL query path.
DROP TRIGGER IF EXISTS trg_search_outbox_question ON questions;
CREATE TRIGGER trg_search_outbox_question
    AFTER INSERT OR DELETE OR UPDATE OF title, answer_html, searchable_text, tags, content_status ON questions
    FOR EACH ROW EXECUTE FUNCTION search_outbox_emit('question');
-- Remove legacy non-public documents on the next relay tick.
INSERT INTO search_outbox(doc_type, doc_id, op)
SELECT 'question', id, 'UPSERT' FROM questions WHERE content_status <> 'PUBLISHED';
