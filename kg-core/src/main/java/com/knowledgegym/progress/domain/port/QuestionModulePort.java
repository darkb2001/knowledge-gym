package com.knowledgegym.progress.domain.port;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface QuestionModulePort {
    Map<UUID, UUID> moduleByQuestion(Collection<UUID> questionIds);
}
