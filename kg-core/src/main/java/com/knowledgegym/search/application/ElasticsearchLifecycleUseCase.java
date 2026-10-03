package com.knowledgegym.search.application;

import com.knowledgegym.search.domain.port.SearchAuditPort;
import com.knowledgegym.search.domain.port.SearchModeSettingsPort;
import com.knowledgegym.search.domain.port.SearchModeSettingsPort.Mode;
import com.knowledgegym.search.domain.port.SearchModeSettingsPort.Settings;
import com.knowledgegym.shared.domain.port.HostScriptPort;
import java.util.UUID;

public final class ElasticsearchLifecycleUseCase {
    private final SearchModeUseCase modes;
    private final HostScriptPort host;
    private final SearchAuditPort audit;

    public ElasticsearchLifecycleUseCase(SearchModeUseCase modes, HostScriptPort host) {
        this(modes, host, (actorId, action, details) -> { });
    }

    public ElasticsearchLifecycleUseCase(SearchModeUseCase modes, HostScriptPort host,
                                         SearchAuditPort audit) {
        this.modes = modes;
        this.host = host;
        this.audit = audit;
    }

    public HostScriptPort.Result status() {
        return host.run("status");
    }

    public Settings stop(UUID actorId) {
        Settings current = modes.current();
        HostScriptPort.Result result = host.run("stop");
        if (!result.success()) {
            throw new IllegalStateException("Failed to stop Elasticsearch: exit "
                    + result.exitCode() + ": " + result.output());
        }
        Settings updated = modes.update(Mode.POSTGRES, current.version(), actorId);
        audit.record(actorId, "ES_STOP", result.output());
        return updated;
    }

    public HostScriptPort.Result start(UUID actorId) {
        HostScriptPort.Result result = host.run("start");
        if (result.success()) {
            audit.record(actorId, "ES_START", result.output());
        }
        return result;
    }
}
