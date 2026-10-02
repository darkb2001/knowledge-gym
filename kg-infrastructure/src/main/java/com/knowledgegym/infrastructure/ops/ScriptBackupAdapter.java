package com.knowledgegym.infrastructure.ops;

import com.knowledgegym.shared.domain.port.BackupPort;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ScriptBackupAdapter implements BackupPort {
    private final Path script;
    private final Duration timeout;

    public ScriptBackupAdapter(
            @Value("${app.ops.backup-script:}") String scriptPath,
            @Value("${app.ops.script-timeout-seconds:900}") long timeoutSeconds) {
        this.script = scriptPath == null || scriptPath.isBlank() ? null : Path.of(scriptPath);
        this.timeout = Duration.ofSeconds(Math.max(30, timeoutSeconds));
    }

    @Override
    public Result run() {
        ScriptProcessSupport.Result result = ScriptProcessSupport.run(script, timeout);
        return new Result(result.success(), result.exitCode(), result.output());
    }
}
