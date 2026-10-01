-- Keep the pre-existing GIN index useful for both existing and newly written notes.
CREATE OR REPLACE FUNCTION notes_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple', coalesce(NEW.content, '') || ' ' || coalesce(array_to_string(NEW.tags, ' '), ''));
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

UPDATE notes SET search_vector = to_tsvector('simple', coalesce(content, '') || ' ' || coalesce(array_to_string(tags, ' '), ''));

CREATE TRIGGER trg_notes_search_vector
    BEFORE INSERT OR UPDATE OF content, tags ON notes
    FOR EACH ROW EXECUTE FUNCTION notes_search_vector_update();
