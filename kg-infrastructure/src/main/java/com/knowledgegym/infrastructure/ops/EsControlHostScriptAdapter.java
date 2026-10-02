package com.knowledgegym.infrastructure.ops;

import com.knowledgegym.shared.domain.port.HostScriptPort;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EsControlHostScriptAdapter implements HostScriptPort {
    private static final Set<String> ALLOWED = Set.of("status", "start", "stop");
    private final Path script;
    private final Duration timeout;

    public EsControlHostScriptAdapter(
            @Value("${app.ops.es-control-script:}") String scriptPath,
            @Value("${app.ops.script-timeout-seconds:120}") long timeoutSeconds) {
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
