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

    public StopResult stop(UUID actorId) {
        Settings current = modes.current();
        HostScriptPort.Result result = host.run("stop");
        if (!result.success()) {
            audit.record(actorId, "ES_STOP_FAILED", auditDetails(result));
            throw new HostOperationException("stop", result);
        }
        Settings updated = modes.update(Mode.POSTGRES, current.version(), actorId);
        audit.record(actorId, "ES_STOP", auditDetails(result));
        return new StopResult(updated, result);
    }

    public HostScriptPort.Result start(UUID actorId) {
        HostScriptPort.Result result = host.run("start");
        audit.record(actorId, result.success() ? "ES_START" : "ES_START_FAILED", auditDetails(result));
        return result;
    }

    private static String auditDetails(HostScriptPort.Result result) {
        String output = result.output() == null ? "" : result.output()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
        return "{\"exitCode\":" + result.exitCode() + ",\"output\":\"" + output + "\"}";
    }

    public record StopResult(Settings settings, HostScriptPort.Result hostResult) {}

    public static final class HostOperationException extends IllegalStateException {
        private final String action;
        private final HostScriptPort.Result result;

        public HostOperationException(String action, HostScriptPort.Result result) {
            super("Failed to " + action + " Elasticsearch: exit "
                    + result.exitCode() + ": " + result.output());
            this.action = action;
            this.result = result;
        }

        public String action() { return action; }
        public HostScriptPort.Result result() { return result; }
    }
}
