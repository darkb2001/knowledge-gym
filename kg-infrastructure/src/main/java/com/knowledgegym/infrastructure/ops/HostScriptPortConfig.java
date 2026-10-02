package com.knowledgegym.infrastructure.ops;

import com.knowledgegym.shared.domain.port.HostScriptPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires exactly one {@link HostScriptPort}: HTTP agent (preferred), host script, or no-op.
 *
 * <p>Do not use {@code @ConditionalOnProperty} on empty compose env placeholders — an empty
 * {@code KG_ES_CONTROL_URL=} still "sets" the property and would activate a broken HTTP client.
 */
@Configuration
public class HostScriptPortConfig {

    @Bean
    HostScriptPort hostScriptPort(
            @Value("${app.ops.es-control-url:}") String controlUrl,
            @Value("${app.ops.es-control-token:}") String controlToken,
            @Value("${app.ops.es-control-script:}") String controlScript,
            @Value("${app.ops.script-timeout-seconds:180}") long timeoutSeconds) {
        if (controlUrl != null && !controlUrl.isBlank()) {
            return new EsControlHttpAdapter(controlUrl, controlToken, timeoutSeconds);
        }
        if (controlScript != null && !controlScript.isBlank()) {
            return new EsControlHostScriptAdapter(controlScript, timeoutSeconds);
        }
        return action -> new HostScriptPort.Result(false, 501,
                "ES control not configured (set app.ops.es-control-url or app.ops.es-control-script)");
    }
}
