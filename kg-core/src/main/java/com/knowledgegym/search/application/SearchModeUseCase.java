package com.knowledgegym.search.application;

import com.knowledgegym.search.domain.port.SearchModeSettingsPort;
import com.knowledgegym.search.domain.port.SearchModeSettingsPort.Mode;
import com.knowledgegym.search.domain.port.SearchModeSettingsPort.Settings;
import com.knowledgegym.shared.application.ConflictException;
import java.util.UUID;

public final class SearchModeUseCase {
    private final SearchModeSettingsPort settings;

    public SearchModeUseCase(SearchModeSettingsPort settings) {
        this.settings = settings;
    }

    public Settings current() {
        return settings.current();
    }

    public Settings update(Mode mode, long expectedVersion, UUID actorId) {
        if (mode == null) throw new IllegalArgumentException("Search mode is required");
        try {
            return settings.update(mode, expectedVersion, actorId);
        } catch (IllegalStateException conflict) {
            throw new ConflictException("Search settings changed; reload before retrying");
        }
    }
}
