package com.knowledgegym.search.application;

import com.knowledgegym.search.domain.port.SearchModeSettingsPort;
import com.knowledgegym.search.domain.port.SearchQueryPort;
import com.knowledgegym.shared.domain.model.SearchHit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fallback is the whole point of this class: search must answer from PostgreSQL when the index
 * is unavailable, and must not answer at all when the query carries no content.
 */
class GlobalSearchUseCaseTest {

    private final UUID user = UUID.randomUUID();
    private final RecordingPort postgres = new RecordingPort(List.of(hit("postgres")));
    private final RecordingPort index = new RecordingPort(List.of(hit("index")));

    @Test
    void prefersTheIndexWhenItIsEnabled() {
        var useCase = new GlobalSearchUseCase(postgres, Optional.of(index));

        assertThat(useCase.search(user, "heap")).extracting(SearchHit::title).containsExactly("index");
        assertThat(postgres.calls).isEmpty();
    }

    /** Flag off is not an error path: the Postgres query is the original implementation. */
    @Test
    void forcedPostgresModeDoesNotCallTheIndex() {
        var useCase = new GlobalSearchUseCase(postgres, Optional.of(index), fixedMode(SearchModeSettingsPort.Mode.POSTGRES));

        assertThat(useCase.search(user, "heap")).extracting(SearchHit::title).containsExactly("postgres");
        assertThat(index.calls).isEmpty();
    }

    @Test
    void usesPostgresWhenTheIndexBeanIsAbsent() {
        var useCase = new GlobalSearchUseCase(postgres, Optional.empty());

        assertThat(useCase.search(user, "heap")).extracting(SearchHit::title).containsExactly("postgres");
    }

    /** AUTO means "prefer the index, degrade to Postgres" — the default production posture. */
    @Test
    void autoModePrefersTheIndex() {
        var useCase = new GlobalSearchUseCase(postgres, Optional.of(index), fixedMode(SearchModeSettingsPort.Mode.AUTO));

        assertThat(useCase.search(user, "heap")).extracting(SearchHit::title).containsExactly("index");
        assertThat(postgres.calls).isEmpty();
    }

    @Test
    void autoModeFallsBackToPostgresWhenTheIndexFails() {
        index.failure = new IllegalStateException("cluster unreachable");
        var useCase = new GlobalSearchUseCase(postgres, Optional.of(index), fixedMode(SearchModeSettingsPort.Mode.AUTO));

        assertThat(useCase.search(user, "heap")).extracting(SearchHit::title).containsExactly("postgres");
    }

    /**
     * The mode and the bean can disagree: an operator can select ELASTICSEARCH while the capability
     * flag is off, so no index bean exists. That must still answer, not NPE.
     */
    @Test
    void elasticsearchModeWithoutAnIndexBeanFallsBackToPostgres() {
        var useCase = new GlobalSearchUseCase(postgres, Optional.empty(),
                fixedMode(SearchModeSettingsPort.Mode.ELASTICSEARCH));

        assertThat(useCase.search(user, "heap")).extracting(SearchHit::title).containsExactly("postgres");
    }

    @Test
    void fallsBackToPostgresWhenTheIndexFails() {
        index.failure = new IllegalStateException("cluster unreachable");
        var useCase = new GlobalSearchUseCase(postgres, Optional.of(index));

        assertThat(useCase.search(user, "heap")).extracting(SearchHit::title).containsExactly("postgres");
    }

    /**
     * A blank query must not reach either backend. Forwarding " " to the index would be a
     * match_all dressed up as a search, which would surface the whole corpus.
     */
    @Test
    void blankQueryReturnsNothingAndTouchesNoBackend() {
        var useCase = new GlobalSearchUseCase(postgres, Optional.of(index));

        assertThat(useCase.search(user, "   ")).isEmpty();
        assertThat(useCase.search(user, null)).isEmpty();
        assertThat(index.calls).isEmpty();
        assertThat(postgres.calls).isEmpty();
    }

    @Test
    void trimsTheQueryAndForwardsTheCaller() {
        var useCase = new GlobalSearchUseCase(postgres, Optional.of(index));

        useCase.search(user, "  heap  ");

        assertThat(index.calls).containsExactly(new Call(user, "heap", 30));
    }

    private static SearchModeSettingsPort fixedMode(SearchModeSettingsPort.Mode mode) {
        return new SearchModeSettingsPort() {
            @Override public SearchModeSettingsPort.Settings current() {
                return new SearchModeSettingsPort.Settings(mode, 0, java.time.Instant.EPOCH, null);
            }
            @Override public SearchModeSettingsPort.Settings update(SearchModeSettingsPort.Mode next, long version, UUID actorId) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static SearchHit hit(String title) {
        return new SearchHit("question", UUID.randomUUID(), title, "excerpt");
    }

    private record Call(UUID user, String query, int limit) {}

    private static final class RecordingPort implements SearchQueryPort {
        private final List<SearchHit> results;
        private final List<Call> calls = new ArrayList<>();
        private RuntimeException failure;

        private RecordingPort(List<SearchHit> results) {
            this.results = results;
        }

        @Override
        public List<SearchHit> search(UUID callerId, String query, int limit) {
            calls.add(new Call(callerId, query, limit));
            if (failure != null) {
                throw failure;
            }
            return results;
        }
    }
}
