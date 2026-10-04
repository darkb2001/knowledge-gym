package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.QuestionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataQuestionRepository extends JpaRepository<QuestionJpaEntity, UUID> {

    List<QuestionJpaEntity> findByModuleId(UUID moduleId);

    Optional<QuestionJpaEntity> findByModuleIdAndSortOrder(UUID moduleId, int sortOrder);

    @Query("SELECT coalesce(max(q.sortOrder), 0) FROM QuestionJpaEntity q WHERE q.moduleId = :moduleId")
    int findMaxSortOrder(@Param("moduleId") UUID moduleId);

    long countByModuleId(UUID moduleId);

    long countByModuleIdAndContentStatus(UUID moduleId, String contentStatus);

    @Query("SELECT q.moduleId, count(q) FROM QuestionJpaEntity q WHERE q.contentStatus = 'PUBLISHED' GROUP BY q.moduleId")
    List<Object[]> countPublishedGroupedByModule();

    void deleteByModuleId(UUID moduleId);

    @Modifying
    @Query("""
            DELETE FROM QuestionJpaEntity q
            WHERE q.moduleId = :moduleId AND q.sortOrder NOT IN :sortOrders
            """)
    int deleteByModuleIdAndSortOrderNotIn(@Param("moduleId") UUID moduleId,
                                          @Param("sortOrders") Collection<Integer> sortOrders);

    /** `moduleId → số câu` trong 1 query — tránh N+1 khi list module. */
    @Query("""
            SELECT q.moduleId, count(q)
            FROM QuestionJpaEntity q
            GROUP BY q.moduleId
            """)
    List<Object[]> countGroupedByModule();

    /**
     * Upsert idempotent theo natural key `(module_id, sort_order)` — unique index ở V014.
     *
     * Cố ý **không** update `difficulty`: admin có thể đã sửa tay qua `PATCH /questions/{id}`,
     * và re-import docs/ không được phép ghi đè quyết định của con người.
     *
     * `search_vector` không nằm trong danh sách cột — trigger `trg_questions_search` (V015)
     * tự tính từ title + answer_html + searchable_text.
     *
     * `tags` truyền dạng literal `{a,b}` rồi cast: tag là slug `[a-z0-9-]` nên không có ký tự
     * cần quote; tránh phụ thuộc cách Hibernate bind mảng vào native query.
     */
    @Modifying
    @Query(value = """
            INSERT INTO questions
                (id, module_id, title, answer_html, difficulty, tags, hints,
                 sort_order, version, created_at, updated_at, searchable_text)
            VALUES
                (:id, :moduleId, :title, :answerHtml, cast(:difficulty as varchar), cast(:tags as text[]), null,
                 :sortOrder, 1, now(), now(), :searchableText)
            ON CONFLICT (module_id, sort_order) DO UPDATE SET
                title           = EXCLUDED.title,
                answer_html     = EXCLUDED.answer_html,
                tags            = EXCLUDED.tags,
                searchable_text = EXCLUDED.searchable_text,
                updated_at      = now(),
                version         = questions.version + 1
            """, nativeQuery = true)
    int upsert(@Param("id") UUID id,
               @Param("moduleId") UUID moduleId,
               @Param("title") String title,
               @Param("answerHtml") String answerHtml,
               @Param("difficulty") String difficulty,
               @Param("tags") String tagsLiteral,
               @Param("sortOrder") int sortOrder,
               @Param("searchableText") String searchableText);
}
