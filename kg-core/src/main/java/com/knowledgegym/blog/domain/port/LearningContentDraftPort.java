package com.knowledgegym.blog.domain.port;

import com.knowledgegym.shared.domain.model.PageResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LearningContentDraftPort {
    enum Kind { QUESTION, FLASHCARD, INTERVIEW }
    enum Status { REVIEW, APPROVED, REJECTED }
    record Evidence(UUID id, String title, String summary, String url) {}
    record Generated(Kind kind, String title, String payloadJson, List<UUID> sourceIds,
                     String model, int tokensUsed, BigDecimal costUsd) {}
    record Draft(UUID id, UUID goalId, Kind kind, String title, String payloadJson,
                 List<UUID> sourceIds, Status status, String model, int tokensUsed,
                 BigDecimal costUsd, Instant createdAt, Instant reviewedAt, String reviewReason) {}
    PageResult<Draft> list(Status status, Kind kind, int page, int size);
    Draft save(UUID actor, UUID goalId, Generated generated);
    Draft get(UUID id);
    Draft review(UUID actor, UUID id, Status status, String reason);
    List<Evidence> evidence(List<UUID> ids);
    void audit(UUID actor, UUID draftId, String action, String reason);
}
