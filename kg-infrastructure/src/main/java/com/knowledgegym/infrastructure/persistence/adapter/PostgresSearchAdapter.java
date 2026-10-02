package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.search.domain.port.SearchQueryPort;
import com.knowledgegym.shared.domain.model.SearchHit;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL full-text search over the {@code search_vector} columns, used as the mandatory
 * backend and as the fallback when the search index is unavailable.
 *
 * <p>Unions three corpora and ranks with {@code ts_rank}. Note visibility is enforced inline
 * ({@code notes WHERE user_id=?}) rather than by a caller-side filter, so there is no code path
 * that could forget it.
 *
 * <p>This query predates the search index and is kept byte-for-byte compatible with it: turning
 * the feature flag off must leave behaviour indistinguishable from before the index existed.
 */
@Component("postgresSearchQuery")
class PostgresSearchAdapter implements SearchQueryPort {

    private final JdbcTemplate jdbc;

    PostgresSearchAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SearchHit> search(UUID userId, String query, int limit) {
        String sql = "WITH ts AS (SELECT plainto_tsquery('simple', ?) q) "
                + "SELECT type,id,title,excerpt FROM ("
                + "SELECT 'question' type,id,title,left(title,240) excerpt,ts_rank(search_vector,ts.q) rank "
                + "FROM questions,ts WHERE search_vector @@ ts.q "
                + "UNION ALL SELECT 'note',id,coalesce(note_type,'Note'),left(coalesce(content,''),240),"
                + "ts_rank(search_vector,ts.q) FROM notes,ts WHERE user_id=? AND search_vector @@ ts.q "
                + "UNION ALL SELECT 'blog',id,title,left(coalesce(excerpt,''),240),ts_rank(search_vector,ts.q) "
                + "FROM blog_posts,ts WHERE status='PUBLISHED' AND search_vector @@ ts.q"
                + ") hits ORDER BY rank DESC LIMIT ?";
        return jdbc.query(sql,
                (rs, n) -> new SearchHit(
                        rs.getString("type"),
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("excerpt")),
                query, userId, limit);
    }
}
