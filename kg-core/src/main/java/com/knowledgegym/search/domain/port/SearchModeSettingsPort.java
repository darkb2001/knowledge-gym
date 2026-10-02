package com.knowledgegym.search.domain.port;

import java.time.Instant;
import java.util.UUID;

public interface SearchModeSettingsPort {
    Settings current();
    Settings update(Mode mode, long expectedVersion, UUID actorId);

    enum Mode { POSTGRES, ELASTICSEARCH, AUTO }

    record Settings(Mode mode, long version, Instant updatedAt, UUID updatedBy) {}
}
