package com.knowledgegym.presentation.rest.admin;

import com.knowledgegym.search.application.SearchModeUseCase;
import com.knowledgegym.search.domain.port.SearchModeSettingsPort.Mode;
import com.knowledgegym.search.domain.port.SearchModeSettingsPort.Settings;
import com.knowledgegym.search.domain.port.SearchQueryPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/search")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSearchController {
    private final SearchModeUseCase modes;
    private final Optional<SearchQueryPort> elasticsearch;

    public AdminSearchController(SearchModeUseCase modes,
                                 @Qualifier("elasticsearchSearchQuery") Optional<SearchQueryPort> elasticsearch) {
        this.modes = modes;
        this.elasticsearch = elasticsearch;
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

    public record UpdateRequest(Mode mode, long version) {}

    public record SearchSettings(Mode mode, long version, java.time.Instant updatedAt,
                                 UUID updatedBy, boolean elasticsearchConfigured) {
        static SearchSettings from(Settings settings, boolean configured) {
            return new SearchSettings(settings.mode(), settings.version(), settings.updatedAt(),
                    settings.updatedBy(), configured);
        }
    }
}
