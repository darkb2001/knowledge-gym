package com.knowledgegym.infrastructure.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import com.knowledgegym.search.domain.model.SearchDocument;
import com.knowledgegym.search.domain.port.SearchModeSettingsPort;
import com.knowledgegym.search.domain.port.SearchModeSettingsPort.Mode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates the search index and its mapping, and seeds it on first creation.
 *
 * <p>The mapping is intentionally minimal, and the reason is the tokenizer. {@code normalized}
 * receives the output of {@code SearchText.build} — lowercase, diacritic-folded, stopwords
 * removed, hyphenated n-grams, expanded synonyms. A language-aware analyzer here would
 * re-tokenize that and split the n-grams back apart, so the field is plain {@code text}: the
 * language rules stay in one place, in Java, and Elasticsearch is used for ranking and scale
 * rather than linguistics.
 *
 * <p>{@code raw} keeps the standard analyzer so English substring and stemming behaviour
 * ("transactions" matching "transaction") is not lost.
 *
 * <p><b>Seeding.</b> When the index is created from scratch it is empty, so the corpus is
 * enqueued into {@code search_outbox} and drained by the relay. Triggers only capture writes that
 * happen *after* they exist, so without this a fresh index would only ever contain content
 * created since the last restart — and content written while the feature was disabled would be
 * absent permanently. Re-running the enqueue is harmless: documents are addressed by id and
 * upserts are idempotent.
 *
 * <p>When runtime mode is {@code POSTGRES} or the cluster is unreachable, reindex is skipped so
 * an app restart with Elasticsearch stopped does not stall boot or enqueue a useless backlog
 * that nothing will drain until the next start.
 */
@Component
@ConditionalOnProperty(name = "app.search.elasticsearch.enabled", havingValue = "true")
public class SearchIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexInitializer.class);

    private final ElasticsearchClient client;
    private final JdbcTemplate jdbc;
    private final SearchModeSettingsPort settings;
    private final String indexName;
    private final boolean reindexOnStartup;

    public SearchIndexInitializer(ElasticsearchClient client, JdbcTemplate jdbc,
                                 SearchModeSettingsPort settings,
                                 @Value("${app.search.elasticsearch.index:knowledge-gym-search}") String indexName,
                                 @Value("${app.search.elasticsearch.reindex-on-startup:true}") boolean reindexOnStartup) {
        this.client = client;
        this.jdbc = jdbc;
        this.settings = settings;
        this.indexName = indexName;
        this.reindexOnStartup = reindexOnStartup;
    }

    public String indexName() {
        return indexName;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialise() {
        try {
            if (settings.current().mode() == Mode.POSTGRES) {
                log.info("Search mode is POSTGRES; skipping Elasticsearch index initialise/reindex");
                return;
            }
            if (!clusterReachable()) {
                log.warn("Elasticsearch unreachable; skipping index initialise/reindex "
                        + "(search stays on PostgreSQL fallback)");
                return;
            }
            boolean created = false;
            if (!client.indices().exists(e -> e.index(indexName)).value()) {
                client.indices().create(c -> c
                        .index(indexName)
                        // One shard: the corpus is small and a single node is the target
                        // deployment. More shards would only add per-shard overhead and merge cost.
                        .settings(IndexSettings.of(s -> s.numberOfShards("1").numberOfReplicas("0")))
                        .mappings(m -> m.properties(properties())));
                created = true;
                log.info("Created search index '{}'", indexName);
            }
            // Re-seeding on every startup is what makes the index self-healing. The triggers only
            // capture writes, so anything written while the feature was disabled, or lost to a
            // full outbox purge, would otherwise be missing from the index permanently. Upserts
            // are idempotent and addressed by id, so re-running costs one bulk request.
            if (created || reindexOnStartup) {
                enqueueFullReindex();
            }
        } catch (Exception e) {
            // Never let this fail startup. An exception thrown from an ApplicationReadyEvent
            // listener propagates and can abort the boot, which would be badly disproportionate:
            // the index only accelerates a query Postgres already answers, and search falls back
            // to Postgres until the index exists.
            log.warn("Search index '{}' not initialised; search will use the PostgreSQL fallback "
                    + "until the index exists", indexName, e);
        }
    }

    private boolean clusterReachable() {
        try {
            client.ping();
            return true;
        } catch (Exception e) {
            log.debug("Elasticsearch ping failed", e);
            return false;
        }
    }

    /** Enqueues every searchable row so the relay indexes it. */
    private void enqueueFullReindex() {
        int enqueued = jdbc.update(
                "INSERT INTO search_outbox(doc_type, doc_id, op) "
                        + "SELECT 'question', id, 'UPSERT' FROM questions "
                        + "UNION ALL SELECT 'note', id, 'UPSERT' FROM notes "
                        + "UNION ALL SELECT 'blog', id, 'UPSERT' FROM blog_posts WHERE status = 'PUBLISHED'");
        if (enqueued > 0) {
            log.info("Enqueued {} documents for initial search indexing", enqueued);
        }
    }

    private static Map<String, Property> properties() {
        Map<String, Property> props = new LinkedHashMap<>();
        props.put(SearchDocument.Fields.TYPE, Property.of(p -> p.keyword(k -> k)));
        props.put(SearchDocument.Fields.TITLE, Property.of(p -> p.text(t -> t)));
        props.put(SearchDocument.Fields.EXCERPT, Property.of(p -> p.text(t -> t)));
        props.put(SearchDocument.Fields.RAW, Property.of(p -> p.text(t -> t)));
        props.put(SearchDocument.Fields.NORMALIZED, Property.of(p -> p.text(t -> t)));
        // Notes are owner-scoped, so this is filtered, not just stored.
        props.put(SearchDocument.Fields.OWNER, Property.of(p -> p.keyword(k -> k)));
        props.put(SearchDocument.Fields.PUBLISHED, Property.of(p -> p.boolean_(b -> b)));
        props.put(SearchDocument.Fields.UPDATED_AT, Property.of(p -> p.date(d -> d)));
        return props;
    }
}
