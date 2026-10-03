package com.knowledgegym.blog.domain.port;

import java.util.List;
import java.util.UUID;

public interface LearningContentAiPort {
    LearningContentDraftPort.Generated generate(LearningContentDraftPort.Kind kind, String topic,
                                                String instruction, List<LearningContentDraftPort.Evidence> evidence);
}
