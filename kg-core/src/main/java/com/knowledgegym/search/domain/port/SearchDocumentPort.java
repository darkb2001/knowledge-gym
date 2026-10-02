package com.knowledgegym.search.domain.port;

import com.knowledgegym.search.domain.model.SearchDocument;
import java.util.Optional;
import java.util.UUID;

/**
 * Loads the current searchable projection of a source row.
 *
 * <p>Used by the index relay, which stores no payload: it re-reads the row by id so the index
 * always reflects committed state. A row that was written and then changed again before the relay
 * ran is therefore indexed once, in its final form, instead of twice in write order.
 */
public interface SearchDocumentPort {

    /**
     * @return the document, or empty when the row is gone or must not be searchable at all
     *     (an archived or not-yet-published blog post). Empty is a delete signal, not an error.
     */
    Optional<SearchDocument> load(SearchDocument.DocType type, UUID id);
}
