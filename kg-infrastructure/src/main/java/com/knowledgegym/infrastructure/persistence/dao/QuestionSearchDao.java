package com.knowledgegym.infrastructure.persistence.dao;

import com.knowledgegym.content.domain.model.QuestionQuery;
import com.knowledgegym.infrastructure.persistence.entity.QuestionJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Truy vấn danh sách câu hỏi — native SQL vì cần trộn 2 loại điều kiện khó diễn đạt bằng
 * JPA Criteria: full-text (`plainto_tsquery` + `ts_rank`) và filter cột thường (`tags` GIN).
 *
 * SQL dựng bằng StringBuilder nhưng **mọi giá trị đều bind qua tham số**, chỉ tên cột/đoạn
 * mệnh đề là hằng số trong code → không có đường injection.
 */
@Component
public class QuestionSearchDao {

    private static final String BASE_FROM = """
            FROM questions q
            JOIN modules m ON m.id = q.module_id
            """;

    @PersistenceContext
    private EntityManager entityManager;

    public List<QuestionJpaEntity> search(QuestionQuery query) { return search(query, false); }

    public List<QuestionJpaEntity> search(QuestionQuery query, boolean admin) {
        StringBuilder sql = new StringBuilder("SELECT q.* ").append(BASE_FROM);
        StringBuilder where = new StringBuilder(admin ? " WHERE 1=1 " : " WHERE q.content_status = 'PUBLISHED' ");

        if (query.moduleId() != null) {
            where.append(" AND q.module_id = :moduleId ");
        }
        if (query.topicId() != null) where.append(" AND m.topic_id = :topicId ");
        if (query.difficulty() != null) {
            where.append(" AND q.difficulty = :difficulty ");
        }
        if (query.tag() != null) {
            where.append(" AND :tag = ANY(q.tags) ");
        }
        if (query.hasFullText()) {
            where.append(" AND q.search_vector @@ plainto_tsquery('simple', :q) ");
        }

        sql.append(where);

        if (query.hasFullText()) {
            sql.append(" ORDER BY ts_rank(q.search_vector, plainto_tsquery('simple', :q)) DESC,")
               .append(" m.display_order, q.sort_order, q.id ");
        } else {
            sql.append(" ORDER BY m.display_order, q.sort_order, q.id ");
        }
        sql.append(" LIMIT :limit OFFSET :offset ");

        Query dataQuery = entityManager.createNativeQuery(sql.toString(), QuestionJpaEntity.class);
        bind(dataQuery, query);
        dataQuery.setParameter("limit", query.size());
        // offset() trả `long`: `(page-1)*size` dạng int tràn thành số âm với page cực lớn,
        // Postgres từ chối OFFSET âm (SQLSTATE 2201X) → API trả 409 "Resource already exists",
        // một message chẳng liên quan gì tới tham số client gửi.
        dataQuery.setParameter("offset", query.offset());

        @SuppressWarnings("unchecked")
        List<QuestionJpaEntity> rows = dataQuery.getResultList();
        return rows;
    }

    public long count(QuestionQuery query) { return count(query, false); }

    public long count(QuestionQuery query, boolean admin) {
        StringBuilder countSql = new StringBuilder("SELECT count(*) ").append(BASE_FROM);
        StringBuilder where = new StringBuilder(admin ? " WHERE 1=1 " : " WHERE q.content_status = 'PUBLISHED' ");
        if (query.moduleId() != null) {
            where.append(" AND q.module_id = :moduleId ");
        }
        if (query.topicId() != null) where.append(" AND m.topic_id = :topicId ");
        if (query.difficulty() != null) {
            where.append(" AND q.difficulty = :difficulty ");
        }
        if (query.tag() != null) {
            where.append(" AND :tag = ANY(q.tags) ");
        }
        if (query.hasFullText()) {
            where.append(" AND q.search_vector @@ plainto_tsquery('simple', :q) ");
        }
        countSql.append(where);

        Query countQuery = entityManager.createNativeQuery(countSql.toString());
        bind(countQuery, query);
        return ((Number) countQuery.getSingleResult()).longValue();
    }

    private static void bind(Query target, QuestionQuery query) {
        if (query.moduleId() != null) {
            target.setParameter("moduleId", query.moduleId());
        }
        if (query.topicId() != null) target.setParameter("topicId", query.topicId());
        if (query.difficulty() != null) {
            target.setParameter("difficulty", query.difficulty().name());
        }
        if (query.tag() != null) {
            target.setParameter("tag", query.tag());
        }
        if (query.hasFullText()) {
            target.setParameter("q", query.q());
        }
    }

    /** Dùng cho test/debug — đếm nhanh theo module. */
    public long countByModule(UUID moduleId) {
        Query q = entityManager.createNativeQuery("SELECT count(*) FROM questions WHERE module_id = :moduleId AND content_status = 'PUBLISHED'");
        q.setParameter("moduleId", moduleId);
        return ((Number) q.getSingleResult()).longValue();
    }
}
