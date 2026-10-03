package com.knowledgegym.search.application;

import com.knowledgegym.search.domain.port.SearchAuditPort;
import com.knowledgegym.search.domain.port.SearchModeSettingsPort;
import com.knowledgegym.search.domain.port.SearchModeSettingsPort.Mode;
import com.knowledgegym.search.domain.port.SearchModeSettingsPort.Settings;
import com.knowledgegym.shared.application.ConflictException;
import java.util.UUID;

public final class SearchModeUseCase {
    private final SearchModeSettingsPort settings;
    private final SearchAuditPort audit;

    public SearchModeUseCase(SearchModeSettingsPort settings) {
        this(settings, (actorId, action, details) -> { });
    }

    public SearchModeUseCase(SearchModeSettingsPort settings, SearchAuditPort audit) {
        this.settings = settings;
        this.audit = audit;
    }

    public Settings current() {
        return settings.current();
    }

    public Settings update(Mode mode, long expectedVersion, UUID actorId) {
        if (mode == null) throw new IllegalArgumentException("Search mode is required");
        try {
            Settings updated = settings.update(mode, expectedVersion, actorId);
            audit.record(actorId, "SEARCH_MODE_CHANGED", "{\"mode\":\"" + mode.name() + "\"}");
            return updated;
        } catch (IllegalStateException conflict) {
            throw new ConflictException("Search settings changed; reload before retrying");
        }
    }
}
