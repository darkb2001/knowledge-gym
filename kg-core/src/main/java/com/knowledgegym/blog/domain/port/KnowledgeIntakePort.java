package com.knowledgegym.blog.domain.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.knowledgegym.shared.domain.model.PageResult;
import java.util.UUID;

public interface KnowledgeIntakePort {
    enum GoalStatus { DRAFT, ACTIVE, PAUSED, ARCHIVED }
    enum RunStatus { QUEUED, RUNNING, REVIEW, PUBLISHED, FAILED, CANCELLED }
    record Goal(UUID id, String name, String topic, String objective, GoalStatus status,
                boolean autoPublish, int dailyItemLimit, BigDecimal dailyCostLimitUsd,
                String scheduleCron, List<String> allowedDomains, UUID createdBy, Instant updatedAt) {}
    record Run(UUID id, UUID goalId, RunStatus status, int discoveredCount, int acceptedCount,
               int rejectedCount, BigDecimal costUsd, String errorMessage, UUID requestedBy, Instant createdAt,
               Instant startedAt, Instant finishedAt) {}
    Goal create(UUID actor, String name, String topic, String objective, boolean autoPublish,
                int dailyItemLimit, BigDecimal dailyCostLimitUsd, String scheduleCron, List<String> domains);
    Goal getGoal(UUID id);
    List<Goal> listGoals(GoalStatus status);
    Goal updateStatus(UUID actor, UUID id, GoalStatus status, String reason);
    Run queueRun(UUID actor, UUID goalId, String reason);
    PageResult<Run> listRuns(UUID goalId, int page, int size);
    Optional<Run> claimNextRun();
    void finishRun(UUID runId, RunStatus status, int discovered, int accepted, int rejected, BigDecimal cost, String error);
    void audit(UUID actor, UUID entityId, String entityType, String action, String reason);
}
