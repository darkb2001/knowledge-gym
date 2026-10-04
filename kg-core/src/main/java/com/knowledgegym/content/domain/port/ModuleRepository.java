package com.knowledgegym.content.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.knowledgegym.content.domain.model.ModuleRef;

public interface ModuleRepository {

    Optional<ModuleRef> findBySlug(String slug);

    Optional<ModuleRef> findById(UUID id);
    default ModuleRef save(ModuleRef module) { throw new UnsupportedOperationException(); }
    default void deleteById(UUID id) { throw new UnsupportedOperationException(); }

    /** Insert hoặc update theo `slug` (natural key) — idempotent cho re-import. */
    ModuleRef saveOrUpdateBySlug(ModuleRef module);

    List<ModuleRef> findByTopicIdOrdered(UUID topicId);

    List<ModuleRef> findAllOrdered();

    /** All statuses, for destructive-operation guards. Do not use for learner-visible totals. */
    long countQuestions(UUID moduleId);

    /**
     * Đếm câu hỏi của **tất cả** module trong 1 query (group by).
     *
     * Dùng cho danh sách: gọi {@link #countQuestions} trong vòng lặp là N+1 — 15 module = 15 query
     * cho một request `GET /modules`. Trả map để module không có câu nào vẫn tra được (0).
     */
    java.util.Map<UUID, Long> countQuestionsByModule();

    /** Learner-visible totals must match the PUBLISHED-only question list. */
    default long countPublishedQuestions(UUID moduleId) { throw new UnsupportedOperationException(); }
    default java.util.Map<UUID, Long> countPublishedQuestionsByModule() { throw new UnsupportedOperationException(); }
}
