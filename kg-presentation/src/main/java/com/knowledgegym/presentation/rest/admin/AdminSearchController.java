package com.knowledgegym.presentation.rest.admin;

import com.knowledgegym.search.application.ElasticsearchLifecycleUseCase;
import com.knowledgegym.search.application.SearchModeUseCase;
import com.knowledgegym.search.domain.port.SearchModeSettingsPort.Mode;
import com.knowledgegym.search.domain.port.SearchModeSettingsPort.Settings;
import com.knowledgegym.search.domain.port.SearchQueryPort;
import com.knowledgegym.shared.domain.port.HostScriptPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/admin/search")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSearchController {
    private final SearchModeUseCase modes;
    private final ElasticsearchLifecycleUseCase lifecycle;
    private final Optional<SearchQueryPort> elasticsearch;
    private final ObjectMapper objectMapper;

    public AdminSearchController(SearchModeUseCase modes,
                                 ElasticsearchLifecycleUseCase lifecycle,
                                 ObjectMapper objectMapper,
                                 @Qualifier("elasticsearchSearchQuery") Optional<SearchQueryPort> elasticsearch) {
        this.modes = modes;
        this.lifecycle = lifecycle;
        this.elasticsearch = elasticsearch;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/settings")
    public SearchSettings settings() {
        return SearchSettings.from(modes.current(), elasticsearch.isPresent());
    }

    @PutMapping("/settings")
    public SearchSettings update(@RequestBody UpdateRequest request,
                                 @org.springframework.security.core.annotation.AuthenticationPrincipal UUID actorId) {
        return SearchSettings.from(modes.update(request.mode(), request.version(), actorId), elasticsearch.isPresent());
    }

    @GetMapping("/elasticsearch/status")
    public HostScriptPort.Result elasticsearchStatus() {
        return lifecycle.status();
    }

    @PostMapping("/elasticsearch/stop")
    public LifecycleResponse stopElasticsearch(
            @org.springframework.security.core.annotation.AuthenticationPrincipal UUID actorId) {
        ElasticsearchLifecycleUseCase.StopResult result = lifecycle.stop(actorId);
        return LifecycleResponse.from(result.hostResult(),
                SearchSettings.from(result.settings(), elasticsearch.isPresent()), objectMapper);
    }

    @PostMapping("/elasticsearch/start")
    public LifecycleResponse startElasticsearch(
            @org.springframework.security.core.annotation.AuthenticationPrincipal UUID actorId) {
        HostScriptPort.Result result = lifecycle.start(actorId);
        if (!result.success()) {
            throw new ElasticsearchLifecycleUseCase.HostOperationException("start", result);
        }
        return LifecycleResponse.from(result, null, objectMapper);
    }

    public record LifecycleResponse(boolean success, int exitCode, Object output,
                                    SearchSettings settings) {
        static LifecycleResponse from(HostScriptPort.Result result, SearchSettings settings,
                                      ObjectMapper objectMapper) {
            Object parsed = result.output();
            if (result.output() != null && !result.output().isBlank()) {
                try {
                    JsonNode json = objectMapper.readTree(result.output());
                    parsed = json;
                } catch (RuntimeException ignored) {
                    // Host script output is still returned as text when not JSON.
                }
            }
            return new LifecycleResponse(result.success(), result.exitCode(), parsed, settings);
        }
    }

    public record UpdateRequest(Mode mode, long version) {}

    public record SearchSettings(Mode mode, long version, java.time.Instant updatedAt,
                                 UUID updatedBy, boolean elasticsearchConfigured) {
        static SearchSettings from(Settings settings, boolean configured) {
            return new SearchSettings(settings.mode(), settings.version(), settings.updatedAt(),
                    settings.updatedBy(), configured);
        }
    }
}
