package com.knowledgegym.search.domain.port;

import java.util.UUID;

/** Audit sink for administrative search mode and Elasticsearch lifecycle changes. */
public interface SearchAuditPort {
    void record(UUID actorId, String action, String details);
}
