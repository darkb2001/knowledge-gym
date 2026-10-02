package com.knowledgegym.shared.domain.port;

/** Allowlisted host helper scripts (e.g. es-control.sh). Never pass arbitrary commands. */
public interface HostScriptPort {
    Result run(String action);

    record Result(boolean success, int exitCode, String output) {}
}
