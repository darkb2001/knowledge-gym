package com.knowledgegym.blog.domain.port;
import java.util.UUID;
public interface LearningDraftMaterializerPort {
    UUID materialize(UUID draftId, UUID moduleId, UUID actor, String reason);
    UUID rollback(UUID draftId, UUID actor, String reason);
    default UUID materializeQuestion(UUID draftId, UUID moduleId, UUID actor, String reason) { return materialize(draftId,moduleId,actor,reason); }
}
