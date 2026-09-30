package com.knowledgegym.content.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.knowledgegym.content.domain.model.ModuleRef;

public interface ModuleRepository {

    Optional<ModuleRef> findBySlug(String slug);

    Optional<ModuleRef> findById(UUID id);

    /** Insert hoặc update theo `slug` (natural key) — idempotent cho re-import. */
    ModuleRef saveOrUpdateBySlug(ModuleRef module);

    List<ModuleRef> findByTopicIdOrdered(UUID topicId);

    List<ModuleRef> findAllOrdered();

    /** Đếm số câu hỏi theo module — dùng cho `GET /modules/{id}` (stats) và UI list. */
    long countQuestions(UUID moduleId);

    /**
     * Đếm câu hỏi của **tất cả** module trong 1 query (group by).
     *
     * Dùng cho danh sách: gọi {@link #countQuestions} trong vòng lặp là N+1 — 15 module = 15 query
     * cho một request `GET /modules`. Trả map để module không có câu nào vẫn tra được (0).
     */
    java.util.Map<UUID, Long> countQuestionsByModule();
}
