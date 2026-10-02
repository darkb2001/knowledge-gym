package com.knowledgegym.search.application;

import com.knowledgegym.search.domain.port.SearchModeSettingsPort;
import com.knowledgegym.search.domain.port.SearchModeSettingsPort.Mode;
import com.knowledgegym.search.domain.port.SearchModeSettingsPort.Settings;
import com.knowledgegym.shared.domain.port.HostScriptPort;
import java.util.UUID;

public final class ElasticsearchLifecycleUseCase {
    private final SearchModeUseCase modes;
    private final HostScriptPort host;

    public ElasticsearchLifecycleUseCase(SearchModeUseCase modes, HostScriptPort host) {
        this.modes = modes;
        this.host = host;
    }

    public HostScriptPort.Result status() {
        return host.run("status");
    }

    public Settings stop(UUID actorId) {
        Settings current = modes.current();
        Settings updated = modes.update(Mode.POSTGRES, current.version(), actorId);
        HostScriptPort.Result result = host.run("stop");
        if (!result.success()) {
            throw new IllegalStateException("Failed to stop Elasticsearch: exit " + result.exitCode());
        }
        return updated;
    }

    public HostScriptPort.Result start() {
        return host.run("start");
    }
}
