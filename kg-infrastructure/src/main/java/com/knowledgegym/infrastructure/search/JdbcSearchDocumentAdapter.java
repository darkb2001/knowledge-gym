package com.knowledgegym.infrastructure.search;

import com.knowledgegym.content.application.SearchText;
import com.knowledgegym.search.domain.model.SearchDocument;
import com.knowledgegym.search.domain.port.SearchDocumentPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads the searchable projection straight from the source tables.
 *
 * <p>Loaded per document on demand rather than carried in the outbox payload, so the index always
 * reflects committed state. It also means the projection is defined in exactly one place: the SQL
 * here is the single description of what "searchable" means for each type.
 *
 * <p>Every {@code normalized} value must be a token stream in the same shape {@code
 * SearchText.normalizeQuery} produces, because that is what the query side sends. Handing the
 * field raw accented text would look correct and quietly destroy Vietnamese matching: the field
 * is indexed with the standard analyzer, so "Bất đồng bộ" becomes the tokens
 * {@code bất / đồng / bộ} and a user typing "khong dong bo" or "đồng bộ" would find nothing.
 */
@Component
@ConditionalOnProperty(name = "app.search.elasticsearch.enabled", havingValue = "true")
public class JdbcSearchDocumentAdapter implements SearchDocumentPort {

    private final JdbcTemplate jdbc;

    public JdbcSearchDocumentAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SearchDocument> load(SearchDocument.DocType type, UUID id) {
        return switch (type) {
            case QUESTION -> jdbc.query(
                    "SELECT id,title,coalesce(answer_html,'') answer_html,"
                            + "coalesce(searchable_text,'') searchable_text,"
                            + "coalesce(array_to_string(tags,' '),'') tags,updated_at "
                            + "FROM questions WHERE id=? AND content_status='PUBLISHED'",
                    (rs, n) -> question(rs), id).stream().findFirst();
            case NOTE -> jdbc.query(
                    "SELECT id,user_id,coalesce(note_type,'Note') note_type,coalesce(content,'') content,"
                            + "coalesce(array_to_string(tags,' '),'') tags,updated_at FROM notes WHERE id=?",
                    (rs, n) -> note(rs), id).stream().findFirst();
            case BLOG -> jdbc.query(
                    "SELECT id,title,coalesce(excerpt,'') excerpt,coalesce(body,'') body,"
                            + "coalesce(array_to_string(tags,' '),'') tags,status,updated_at "
                            + "FROM blog_posts WHERE id=?",
                    (rs, n) -> blog(rs), id).stream().findFirst().flatMap(d -> d);
        };
    }

    private static SearchDocument question(ResultSet rs) throws SQLException {
        String title = rs.getString("title");
        String answerHtml = rs.getString("answer_html");
        String searchable = rs.getString("searchable_text");
        String answerText = SearchText.stripHtml(answerHtml);

        // `searchable_text` is already the SearchText.build output for this row — title-derived
        // n-grams, tag expansion and the VI-EN synonyms included — so it is used verbatim. The
        // recompute only covers rows written without it (direct SQL, older data), where an empty
        // normalized field would make the question permanently unfindable in the index.
        String normalized = searchable.isBlank()
                ? SearchText.build(title, answerText, rs.getString("tags"))
                : searchable;

        return new SearchDocument(
                SearchDocument.DocType.QUESTION,
                rs.getObject("id", UUID.class),
                title,
                excerpt(answerText),
                title + " " + answerText,
                normalized,
                null,
                null,
                instant(rs));
    }

    private static SearchDocument note(ResultSet rs) throws SQLException {
        String content = rs.getString("content");
        String tags = rs.getString("tags");
        String displayTitle = readableType(rs.getString("note_type"));

        // Notes have no stored token column: `search_vector` holds a tsvector, not the token text,
        // so the tokens are rebuilt. Content is passed as the n-gram source rather than the answer
        // slot, because a note is short enough that multi-word phrases in it are worth indexing -
        // the opposite trade-off from the long question answers.
        return new SearchDocument(
                SearchDocument.DocType.NOTE,
                rs.getObject("id", UUID.class),
                displayTitle,
                excerpt(content),
                content + " " + tags,
                SearchText.build(content, null, tags),
                rs.getObject("user_id", UUID.class),
                null,
                instant(rs));
    }

    /** Empty when the post is not published: the caller turns that into a delete from the index. */
    private static Optional<SearchDocument> blog(ResultSet rs) throws SQLException {
        if (!"PUBLISHED".equals(rs.getString("status"))) {
            return Optional.empty();
        }
        String title = rs.getString("title");
        String excerpt = rs.getString("excerpt");
        String body = rs.getString("body");
        String tags = rs.getString("tags");
        return Optional.of(new SearchDocument(
                SearchDocument.DocType.BLOG,
                rs.getObject("id", UUID.class),
                title,
                excerpt(excerpt.isBlank() ? body : excerpt),
                title + " " + SearchText.stripHtml(body) + " " + tags,
                SearchText.build(title, body, tags),
                null,
                Boolean.TRUE,
                instant(rs)));
    }

    private static String instant(ResultSet rs) throws SQLException {
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return updatedAt == null ? null : updatedAt.toInstant().toString();
    }

    private static String readableType(String noteType) {
        return noteType == null ? "Note" : noteType;
    }

    private static String excerpt(String text) {
        String flattened = text.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return flattened.length() <= 240 ? flattened : flattened.substring(0, 240);
    }
}
