package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.infrastructure.persistence.entity.UserProgressJpaEntity;
import com.knowledgegym.infrastructure.persistence.repository.SpringDataUserProgressRepository;
import com.knowledgegym.progress.domain.model.UserProgress;
import com.knowledgegym.progress.domain.port.UserProgressRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Native upsert avoids the read/insert race on UNIQUE(user_id, module_id). */
@Repository
public class ProgressRepositoryAdapter implements UserProgressRepository {
    private final SpringDataUserProgressRepository repository;
    @PersistenceContext private EntityManager entityManager;

    public ProgressRepositoryAdapter(SpringDataUserProgressRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserProgress> find(UUID userId, UUID moduleId) {
        return repository.findByUserIdAndModuleId(userId, moduleId).map(ProgressRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserProgress> findAllByUser(UUID userId) {
        return entityManager.createQuery("select p from UserProgressJpaEntity p where p.userId = :userId",
                        UserProgressJpaEntity.class).setParameter("userId", userId).getResultList()
                .stream().map(ProgressRepositoryAdapter::toDomain).toList();
    }

    /**
     * Cumulative mastery is computed from the running totals, so an update never loses earlier
     * attempts. Uses {@code CAST(... AS numeric)} rather than PostgreSQL's {@code ::} shorthand:
     * Hibernate treats {@code :} as part of a named parameter and would parse
     * {@code :correctDelta::numeric} as a single parameter named {@code correctDelta::numeric},
     * failing at runtime with "No argument for named parameter".
     */
    @Override
    @Transactional
    public void upsertAdding(UUID userId, UUID moduleId, int attemptsDelta, int correctDelta, Instant activeAt) {
        entityManager.createNativeQuery("""
                INSERT INTO user_progress
                    (id, user_id, module_id, total_attempts, correct_count, mastery_pct,
                     streak_days, last_active_at, updated_at)
                VALUES (gen_random_uuid(), :userId, :moduleId, :attemptsDelta, :correctDelta,
                        CASE WHEN :attemptsDelta = 0 THEN 0
                             ELSE ROUND(CAST(:correctDelta AS numeric) * 100 / :attemptsDelta, 2) END,
                        0, :activeAt, NOW())
                ON CONFLICT (user_id, module_id) DO UPDATE SET
                    total_attempts = user_progress.total_attempts + :attemptsDelta,
                    correct_count = user_progress.correct_count + :correctDelta,
                    mastery_pct = CASE
                        WHEN user_progress.total_attempts + :attemptsDelta = 0 THEN 0
                        ELSE ROUND(CAST(user_progress.correct_count + :correctDelta AS numeric) * 100 /
                                   (user_progress.total_attempts + :attemptsDelta), 2)
                    END,
                    last_active_at = GREATEST(user_progress.last_active_at, :activeAt),
                    updated_at = NOW()
                """).setParameter("userId", userId).setParameter("moduleId", moduleId)
                .setParameter("attemptsDelta", attemptsDelta).setParameter("correctDelta", correctDelta)
                .setParameter("activeAt", activeAt).executeUpdate();
    }

    private static UserProgress toDomain(UserProgressJpaEntity entity) {
        return new UserProgress(entity.getUserId(), entity.getModuleId(), entity.getMasteryPct(),
                entity.getTotalAttempts(), entity.getCorrectCount(), entity.getLastActiveAt());
    }
}
