-- Broaden notes trigger so updated_at advances on note_type / question_id / module_id
-- changes (list ORDER BY updated_at DESC), not only content/tags edits.
CREATE OR REPLACE FUNCTION notes_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple', coalesce(NEW.content, '') || ' ' || coalesce(array_to_string(NEW.tags, ' '), ''));
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_notes_search_vector ON notes;
CREATE TRIGGER trg_notes_search_vector
    BEFORE INSERT OR UPDATE ON notes
    FOR EACH ROW EXECUTE FUNCTION notes_search_vector_update();
