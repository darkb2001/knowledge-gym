package com.knowledgegym.infrastructure.search;

import com.knowledgegym.search.domain.model.SearchDocument;
import com.knowledgegym.search.domain.port.SearchDocumentPort;
import com.knowledgegym.search.domain.port.SearchIndexPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Drains {@code search_outbox} into the search index.
 *
 * <p>Same shape as {@code OutboxKafkaPublisher}: rows are claimed in a short transaction with
 * {@code FOR UPDATE SKIP LOCKED}, and the network call to the index happens outside any
 * transaction so a slow cluster cannot pin row locks.
 *
 * <p>Rows carry no payload — the source row is re-read at delivery time. That has two
 * consequences worth being explicit about:
 *
 * <ul>
 *   <li>A document written twice before the relay runs is indexed once, in its latest state,
 *       rather than twice in write order. This is why the relay does not need a version check.
 *   <li>A row that has since become unsearchable (blog post archived, note deleted) loads as
 *       empty and is deleted from the index. {@code DELETE} events are therefore a fast path,
 *       not a special case.
 *
 * </ul>
 *
 * <p>Batch ordering is by {@code seq}, not {@code occurred_at}. {@code NOW()} is only
 * second-resolution, so every row written in the same tick would tie and the collapse below would
 * pick an arbitrary winner.
 */
@Component
@ConditionalOnProperty(name = "app.search.elasticsearch.enabled", havingValue = "true")
public class SearchIndexRelay {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexRelay.class);
    private static final int BATCH = 50;
    private static final int MAX_ATTEMPTS = 8;

    private final JdbcTemplate jdbc;
    private final SearchDocumentPort documents;
    private final SearchIndexPort index;
    private final TransactionTemplate tx;

    public SearchIndexRelay(JdbcTemplate jdbc, SearchDocumentPort documents,
                            SearchIndexPort index, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.documents = documents;
        this.index = index;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.search.elasticsearch.poll-ms:5000}")
    public void drain() {
        List<Pending> claimed = claimBatch();
        if (claimed == null || claimed.isEmpty()) {
            return;
        }
        // One index call per relay tick instead of one per row. The bulk request also makes the
        // whole batch's failure atomic, so a partial write cannot leave rows marked processed.
        List<Pending> deliverable = new ArrayList<>();
        for (Pending row : claimed) {
            if (row.attempts() > MAX_ATTEMPTS) {
                // Stop retrying, but never delete or silently mark the row: the outstanding
                // document is real drift, and a human needs to be able to see which one. Logged
                // only on the transition, otherwise a permanently stuck row would emit an error
                // every poll interval forever.
                recordFailure(row.id(), "retry limit reached: " + row.lastError());
                if (row.attempts() == MAX_ATTEMPTS + 1) {
                    log.error("Search index row {} for {}:{} exceeded {} attempts and is stuck",
                            row.id(), row.docType(), row.docId(), MAX_ATTEMPTS);
                }
                continue;
            }
            deliverable.add(row);
        }
        if (deliverable.isEmpty()) {
            return;
        }

        List<SearchDocument> upserts = new ArrayList<>();
        List<SearchIndexPort.DocumentRef> deletes = new ArrayList<>();
        for (Pending row : deliverable) {
            resolve(row, upserts, deletes);
        }

        try {
            if (!deletes.isEmpty()) {
                index.delete(deletes);
            }
            if (!upserts.isEmpty()) {
                index.index(upserts);
            }
            markProcessed(deliverable);
        } catch (RuntimeException failure) {
            String message = failure.getClass().getSimpleName() + ": "
                    + (failure.getMessage() == null ? "index delivery failed" : failure.getMessage());
            for (Pending row : deliverable) {
                recordFailure(row.id(), message);
            }
            log.warn("Search index batch failed, will retry ({} rows)", deliverable.size(), failure);
        }
    }

    /** Turns one claimed row into either an upsert or a delete for its document. */
    private void resolve(Pending row, List<SearchDocument> upserts, List<SearchIndexPort.DocumentRef> deletes) {
        SearchDocument.DocType type = toType(row);
        if (type == null) {
            // An unknown doc_type is a programming error, not a transient failure; drop it so the
            // relay does not spin on the same row forever.
            markProcessed(List.of(row));
            log.error("Unknown search_outbox doc_type '{}' on row {}, dropping", row.docType(), row.id());
            return;
        }
        if ("DELETE".equals(row.op())) {
            deletes.add(new SearchIndexPort.DocumentRef(type, row.docId()));
            return;
        }
        documents.load(type, row.docId()).ifPresentOrElse(
                upserts::add,
                // Absent or no longer searchable: the index must lose the document.
                () -> deletes.add(new SearchIndexPort.DocumentRef(type, row.docId())));
    }

    private static SearchDocument.DocType toType(Pending row) {
        return switch (row.docType()) {
            case "question" -> SearchDocument.DocType.QUESTION;
            case "note" -> SearchDocument.DocType.NOTE;
            case "blog" -> SearchDocument.DocType.BLOG;
            default -> null;
        };
    }

    /**
     * Claims pending rows, collapsing to the newest event per document.
     *
     * <p>The claim marks attempts immediately so a crash mid-batch retries the row rather than
     * losing it, and so a concurrent relay cannot pick the same row up.
     */
    private List<Pending> claimBatch() {
        return tx.execute(status -> {
            List<Pending> rows = jdbc.query(
                    "SELECT id,doc_type,doc_id,op,seq,attempts,last_error FROM search_outbox "
                            + "WHERE processed_at IS NULL ORDER BY seq LIMIT " + BATCH
                            + " FOR UPDATE SKIP LOCKED",
                    (rs, n) -> new Pending(
                            rs.getObject("id", UUID.class),
                            rs.getString("doc_type"),
                            rs.getObject("doc_id", UUID.class),
                            rs.getString("op"),
                            rs.getLong("seq"),
                            rs.getInt("attempts") + 1,
                            rs.getString("last_error")));
            for (Pending row : rows) {
                jdbc.update("UPDATE search_outbox SET attempts=? WHERE id=?", row.attempts(), row.id());
            }
            // Collapse by document, keeping the highest seq: a document touched several times in
            // one batch is written once in its final state.
            Map<String, Pending> newest = new LinkedHashMap<>();
            for (Pending row : rows) {
                String key = row.docType() + ":" + row.docId();
                Pending existing = newest.get(key);
                if (existing == null || row.seq() > existing.seq()) {
                    newest.put(key, row);
                }
            }
            // Superseded rows still need closing out, otherwise they are re-read every tick.
            List<Pending> superseded = new ArrayList<>(rows);
            superseded.removeAll(newest.values());
            for (Pending row : superseded) {
                jdbc.update("UPDATE search_outbox SET processed_at=NOW(),last_error=NULL WHERE id=?", row.id());
            }
            return List.copyOf(newest.values());
        });
    }

    private void markProcessed(List<Pending> rows) {
        tx.executeWithoutResult(status -> {
            for (Pending row : rows) {
                jdbc.update("UPDATE search_outbox SET processed_at=NOW(),last_error=NULL WHERE id=?", row.id());
            }
        });
    }

    private void recordFailure(UUID id, String message) {
        String trimmed = message.substring(0, Math.min(500, message.length()));
        tx.executeWithoutResult(status ->
                jdbc.update("UPDATE search_outbox SET last_error=? WHERE id=?", trimmed, id));
    }

    private record Pending(UUID id, String docType, UUID docId, String op, long seq,
                           int attempts, String lastError) {}
}
