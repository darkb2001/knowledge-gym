package com.knowledgegym.infrastructure.ops;

import com.knowledgegym.shared.domain.port.HostScriptPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

/**
 * Calls the CT102 {@code kg-es-control} HTTP agent on the Docker bridge gateway.
 *
 * <p>App containers cannot reach {@code 127.0.0.1} on the LXC host. Bind the agent to the
 * compose bridge gateway (e.g. {@code 172.18.0.1:9377}) and set {@code app.ops.es-control-url}.
 * Never mount {@code docker.sock} into the app.
 *
 * <p>Constructed by {@link HostScriptPortConfig} — not a {@code @Component}.
 */
public final class EsControlHttpAdapter implements HostScriptPort {
    private static final Set<String> ALLOWED = Set.of("status", "start", "stop");

    private final HttpClient http;
    private final URI base;
    private final String token;
    private final Duration timeout;

    public EsControlHttpAdapter(String baseUrl, String token, long timeoutSeconds) {
        this.base = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        this.token = token == null ? "" : token;
        this.timeout = Duration.ofSeconds(Math.max(10, timeoutSeconds));
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @Override
    public Result run(String action) {
        if (action == null || !ALLOWED.contains(action)) {
            return new Result(false, 2, "Unsupported action: " + action);
        }
        if (token.isBlank()) {
            return new Result(false, 3, "app.ops.es-control-token is not configured");
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(base.resolve(action))
                    .timeout(timeout)
                    .header("X-KG-Token", token)
                    .header("Accept", "text/plain, application/json");
            HttpRequest request = "status".equals(action)
                    ? builder.GET().build()
                    : builder.POST(HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = response.body() == null ? "" : response.body().trim();
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            return new Result(ok, response.statusCode(), body);
        } catch (Exception e) {
            return new Result(false, 1, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
