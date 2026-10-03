package com.knowledgegym.infrastructure.ops;

import com.knowledgegym.shared.domain.port.HostScriptPort;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

/** ProcessBuilder fallback when the JVM shares the CT102 host (not the app container). */
public final class EsControlHostScriptAdapter implements HostScriptPort {
    private static final Set<String> ALLOWED = Set.of("status", "start", "stop");
    private final Path script;
    private final Duration timeout;

    public EsControlHostScriptAdapter(String scriptPath, long timeoutSeconds) {
        this.script = scriptPath == null || scriptPath.isBlank() ? null : Path.of(scriptPath);
        this.timeout = Duration.ofSeconds(Math.max(10, timeoutSeconds));
    }

    @Override
    public Result run(String action) {
        if (action == null || !ALLOWED.contains(action)) {
            return new Result(false, 2, "Unsupported action: " + action);
        }
        ScriptProcessSupport.Result result = ScriptProcessSupport.run(script, timeout, action);
        return new Result(result.success(), result.exitCode(), result.output());
    }
}
