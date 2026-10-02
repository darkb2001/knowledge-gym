package com.knowledgegym.infrastructure.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.knowledgegym.content.application.SearchText;
import com.knowledgegym.search.domain.model.SearchDocument;
import com.knowledgegym.search.domain.port.SearchIndexPort;
import com.knowledgegym.search.domain.port.SearchQueryPort;
import com.knowledgegym.shared.domain.model.SearchHit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch-backed search index, implementing both the read and write ports.
 *
 * <p>Queries are built from {@code SearchText.normalizeQuery}, the same function that produced the
 * indexed {@code normalized} field. Skipping that step is the classic way to break this feature:
 * the index holds {@code dong bo} (stopword "không" removed, diacritics folded), so sending the
 * raw query "không đồng bộ" would be analysed into {@code khong dong bo} and match nothing. The
 * escalation is cheap and the failure mode is invisible — results just quietly stop matching.
 *
 * <p><b>Writes refresh before returning.</b> Elasticsearch is near-real-time: a bulk request
 * acknowledges before the documents become searchable, and the gap is only on the order of the
 * index refresh interval (1s). That is invisible in production but it breaks the invariant the
 * relay relies on — once a row is marked processed, its document must be queryable, otherwise the
 * read path and the write path disagree for an arbitrary window. Waiting for the refresh costs the
 * relay at most one refresh interval per tick and runs entirely off the user request path, which is
 * a very good trade for making delivery synchronous with visibility.
 */
@Component("elasticsearchSearchQuery")
@ConditionalOnProperty(name = "app.search.elasticsearch.enabled", havingValue = "true")
public class ElasticsearchSearchAdapter implements SearchIndexPort, SearchQueryPort {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchSearchAdapter.class);

    private final ElasticsearchClient client;
    private final String indexName;

    public ElasticsearchSearchAdapter(ElasticsearchClient client, SearchIndexInitializer initializer) {
        this.client = client;
        this.indexName = initializer.indexName();
    }

    @Override
    public void index(Collection<SearchDocument> documents) {
        if (documents.isEmpty()) {
            return;
        }
        List<BulkOperation> operations = new ArrayList<>(documents.size());
        for (SearchDocument document : documents) {
            IndexOperation<Map<String, Object>> index = IndexOperation.of(o -> o
                    .index(indexName)
                    .id(document.documentId())
                    .document(toSource(document)));
            operations.add(BulkOperation.of(b -> b.index(index)));
        }
        BulkResponse response = execute(BulkRequest.of(b -> b
                .refresh(Refresh.WaitFor)
                .operations(operations)));
        if (response.errors()) {
            // Reported failures carry the document id, which is what a human needs to reconcile
            // the index by hand. The message is truncated because ES echoes the whole document.
            StringBuilder failures = new StringBuilder();
            for (BulkResponseItem item : response.items()) {
                if (item.error() != null) {
                    failures.append(item.id()).append(": ").append(item.error().reason()).append("; ");
                }
            }
            throw new IllegalStateException("Bulk index partially failed: "
                    + failures.substring(0, Math.min(500, failures.length())));
        }
    }

    @Override
    public void delete(Collection<DocumentRef> refs) {
        if (refs.isEmpty()) {
            return;
        }
        List<BulkOperation> operations = new ArrayList<>(refs.size());
        for (DocumentRef ref : refs) {
            operations.add(BulkOperation.of(b -> b.delete(d -> d.index(indexName).id(ref.documentId()))));
        }
        BulkResponse response = execute(BulkRequest.of(b -> b
                .refresh(Refresh.WaitFor)
                .operations(operations)));
        if (response.errors()) {
            // 404 is not an error here: the document being absent is the desired end state, and
            // the relay would otherwise retry a delete that is already satisfied, forever.
            StringBuilder failures = new StringBuilder();
            for (BulkResponseItem item : response.items()) {
                if (item.error() != null && item.status() != 404) {
                    failures.append(item.id()).append(": ").append(item.error().reason()).append("; ");
                }
            }
            if (!failures.isEmpty()) {
                throw new IllegalStateException("Bulk delete partially failed: "
                        + failures.substring(0, Math.min(500, failures.length())));
            }
        }
    }

    @Override
    public List<SearchHit> search(UUID callerId, String query, int limit) {
        String normalized = SearchText.normalizeQuery(query);
        if (normalized.isBlank()) {
            // Every term was a stopword ("là gì", "what is it"). Returning early keeps the index
            // from being asked a match_all, which would surface the entire corpus.
            return List.of();
        }
        try {
            var response = client.search(s -> s
                    .index(indexName)
                    .size(limit)
                    .query(buildQuery(callerId, normalized)), Map.class);

            List<SearchHit> hits = new ArrayList<>(response.hits().hits().size());
            for (Hit<Map> hit : response.hits().hits()) {
                Map<?, ?> source = hit.source();
                if (source == null) {
                    continue;
                }
                hits.add(new SearchHit(
                        string(source.get(SearchDocument.Fields.TYPE)),
                        UUID.fromString(idFrom(hit)),
                        string(source.get(SearchDocument.Fields.TITLE)),
                        string(source.get(SearchDocument.Fields.EXCERPT))));
            }
            return hits;
        } catch (IOException e) {
            throw new UncheckedIOException("Search query failed", e);
        }
    }

    /**
     * Visibility is a filter, never a scoring input: a note must be invisible to other callers
     * regardless of how well it matches, so the constraint cannot be a should-clause that a good
     * score could outweigh.
     */
    private Query buildQuery(UUID callerId, String normalized) {
        Query visible = Query.of(q -> q.bool(b -> b
                .should(s -> s.term(t -> t.field(SearchDocument.Fields.TYPE)
                        .value(SearchDocument.DocType.QUESTION.wireName())))
                .should(s -> s.term(t -> t.field(SearchDocument.Fields.TYPE)
                        .value(SearchDocument.DocType.BLOG.wireName())))
                .should(s -> s.bool(inner -> inner
                        .filter(f -> f.term(t -> t.field(SearchDocument.Fields.TYPE)
                                .value(SearchDocument.DocType.NOTE.wireName())))
                        .filter(f -> f.term(t -> t.field(SearchDocument.Fields.OWNER)
                                .value(callerId.toString())))))
                .minimumShouldMatch("1")));

        Query matching = Query.of(q -> q.bool(b -> b
                .should(s -> s.match(m -> m.field(SearchDocument.Fields.NORMALIZED).query(normalized).boost(2.0f)))
                .should(s -> s.match(m -> m.field(SearchDocument.Fields.TITLE).query(normalized)))
                .should(s -> s.match(m -> m.field(SearchDocument.Fields.RAW).query(normalized)))
                .minimumShouldMatch("1")));

        return Query.of(q -> q.bool(b -> b.filter(visible).must(matching)));
    }

    private Map<String, Object> toSource(SearchDocument document) {
        Map<String, Object> source = new java.util.LinkedHashMap<>();
        source.put(SearchDocument.Fields.TYPE, document.type().wireName());
        source.put(SearchDocument.Fields.TITLE, document.title());
        source.put(SearchDocument.Fields.EXCERPT, document.excerpt());
        source.put(SearchDocument.Fields.RAW, document.rawText());
        source.put(SearchDocument.Fields.NORMALIZED, document.normalizedText());
        if (document.ownerId() != null) {
            source.put(SearchDocument.Fields.OWNER, document.ownerId().toString());
        }
        if (document.published() != null) {
            source.put(SearchDocument.Fields.PUBLISHED, document.published());
        }
        source.put(SearchDocument.Fields.UPDATED_AT, document.updatedAt());
        return source;
    }

    private BulkResponse execute(BulkRequest request) {
        try {
            return client.bulk(request);
        } catch (IOException e) {
            throw new UncheckedIOException("Bulk index request failed", e);
        }
    }

    /** The document id is `type:uuid`; the row id is what the API contract returns. */
    private static String idFrom(Hit<Map> hit) {
        String id = hit.id();
        int separator = id.indexOf(':');
        return separator < 0 ? id : id.substring(separator + 1);
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }
}
