package com.knowledgegym.content.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.knowledgegym.content.domain.model.Topic;

public interface TopicRepository {

    Optional<Topic> findBySlug(String slug);
    default Optional<Topic> findById(UUID id) { throw new UnsupportedOperationException(); }
    default Topic save(Topic topic) { throw new UnsupportedOperationException(); }
    default void deleteById(UUID id) { throw new UnsupportedOperationException(); }

    /** Insert hoặc update theo `slug` (natural key) — idempotent cho re-import. */
    Topic saveOrUpdateBySlug(Topic topic);

    List<Topic> findAllOrdered();
}
