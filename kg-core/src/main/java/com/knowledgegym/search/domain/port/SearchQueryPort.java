package com.knowledgegym.search.domain.port;

import com.knowledgegym.shared.domain.model.SearchHit;
import java.util.List;
import java.util.UUID;

/**
 * Read side of the search index.
 *
 * <p>Implementations are expected to fail fast on transport errors. Deciding whether to fall back
 * to Postgres belongs to the caller, not here: this port never silently returns an empty list for
 * a backend that is down, because "no results" and "search is unavailable" must be
 * distinguishable.
 */
public interface SearchQueryPort {

    /**
     * @param callerId the authenticated user. Notes are owner-scoped, so this is always applied;
     *                 it cannot be omitted or one user would see another's notes.
     * @param query    the raw user query. Normalisation is the implementation's job, so that the
     *                 same {@code SearchText.normalizeQuery} used at index time is applied here.
     * @param limit    maximum hits to return.
     */
    List<SearchHit> search(UUID callerId, String query, int limit);
}
