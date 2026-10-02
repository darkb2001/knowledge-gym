package com.knowledgegym.search.domain.port;

import com.knowledgegym.search.domain.model.SearchDocument;
import java.util.Collection;
import java.util.UUID;

/**
 * Write side of the search index: applies document changes.
 *
 * <p>Separate from {@link SearchQueryPort} because the two have opposite failure semantics.
 * Writes are best-effort and retried by the relay, so a failure must propagate and be recorded;
 * reads must never fail the request, because a search backend that is down should degrade to
 * Postgres rather than return a 500.
 */
public interface SearchIndexPort {

    /** Upserts documents. A failure aborts the whole batch so the relay can retry it. */
    void index(Collection<SearchDocument> documents);

    /** Removes documents by their {@link DocumentRef#documentId()}. */
    void delete(Collection<DocumentRef> refs);

    /** Type + id pair identifying a document in the shared index. */
    record DocumentRef(SearchDocument.DocType type, UUID id) {
        public String documentId() {
            return SearchDocument.documentId(type, id);
        }
    }
}
