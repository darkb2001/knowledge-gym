package com.knowledgegym.search.application;

import com.knowledgegym.search.domain.port.SearchQueryPort;
import com.knowledgegym.shared.domain.model.SearchHit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global search across questions, the caller's notes and published blog posts.
 *
 * <p>Two implementations of the same {@link SearchQueryPort} are injected: the mandatory
 * PostgreSQL one and the optional Elasticsearch one. They are alternatives, not layers, which is
 * why the preferred backend is an {@code Optional} rather than a required collaborator — the
 * feature flag decides which is used, not whether search exists.
 *
 * <p>The index degrades to PostgreSQL on any failure. Search is a read path: returning
 * stale-or-shallower results beats failing the request, so an index outage must not surface as a
 * 500.
 *
 * <p>The fallback is not silent. A degraded read is a normal event (cluster restart, mapping
 * error, network blip) that would otherwise be indistinguishable from "no matches", so it is
 * logged at WARN and can be alerted on.
 */
public class GlobalSearchUseCase {

    private static final Logger log = LoggerFactory.getLogger(GlobalSearchUseCase.class);

    /** Matches the limit the PostgreSQL query has always applied. */
    private static final int LIMIT = 30;

    private final SearchQueryPort postgres;
    private final Optional<SearchQueryPort> index;

    public GlobalSearchUseCase(SearchQueryPort postgres, Optional<SearchQueryPort> index) {
        this.postgres = postgres;
        this.index = index;
    }

    /**
     * Deliberately not {@code @Transactional}. The index call is remote I/O that does not touch
     * the datasource, and wrapping it would pin a Hikari connection for the whole round trip — a
     * slow cluster would starve the pool. The PostgreSQL fallback is a single statement and needs
     * no explicit transaction of its own.
     */
    public List<SearchHit> search(UUID userId, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String trimmed = query.trim();
        if (index.isPresent()) {
            try {
                return index.get().search(userId, trimmed, LIMIT);
            } catch (RuntimeException unavailable) {
                // Deliberately broad: every failure mode (transport, timeout, mapping error, a
                // malformed query) means the same thing to the caller - fall back and carry on.
                log.warn("Search index unavailable, serving from PostgreSQL fallback: {}",
                        unavailable.toString());
            }
        }
        return postgres.search(userId, trimmed, LIMIT);
    }
}
