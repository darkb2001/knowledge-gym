package com.knowledgegym.infrastructure.ops;

import com.knowledgegym.shared.domain.port.HostScriptPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** Fallback when neither HTTP agent URL nor host script path is configured. */
@Component
@ConditionalOnMissingBean(HostScriptPort.class)
public class NoOpHostScriptAdapter implements HostScriptPort {
    @Override
    public Result run(String action) {
        return new Result(false, 501,
                "ES control not configured (set app.ops.es-control-url or app.ops.es-control-script)");
    }
}
