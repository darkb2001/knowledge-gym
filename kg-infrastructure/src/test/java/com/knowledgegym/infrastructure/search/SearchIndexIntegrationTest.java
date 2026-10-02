package com.knowledgegym.infrastructure.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.knowledgegym.content.application.SearchText;
import com.knowledgegym.search.domain.model.SearchDocument;
import com.knowledgegym.search.domain.port.SearchIndexPort;
import com.knowledgegym.shared.domain.model.SearchHit;
import java.util.List;
import java.util.UUID;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end check of the search index: Postgres triggers → outbox → relay → Elasticsearch →
 * query, including the Vietnamese token parity that the whole design hinges on.
 *
 * <p>Runs on CI and locally when a Docker daemon is available.
 */
@Testcontainers
class SearchIndexIntegrationTest {

    private static final String INDEX = "knowledge-gym-search-test";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("search_test").withUsername("test").withPassword("test");

    @Container
    static final ElasticsearchContainer ELASTICSEARCH =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.11.4")
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("discovery.type", "single-node")
                    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static RestClient restClient;
    static ElasticsearchClient client;
    static ElasticsearchSearchAdapter adapter;
    static JdbcSearchDocumentAdapter documents;
    static SearchIndexRelay relay;
    static UUID userId;
    static UUID moduleId;

    @BeforeAll
    static void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        restClient = RestClient.builder(HttpHost.create(ELASTICSEARCH.getHttpHostAddress())).build();
        client = new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
        var initializer = new SearchIndexInitializer(client, jdbc, INDEX, false);
        initializer.initialise();

        adapter = new ElasticsearchSearchAdapter(client, initializer);
        documents = new JdbcSearchDocumentAdapter(jdbc);
        relay = new SearchIndexRelay(jdbc, documents, adapter, tx);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (restClient != null) {
            restClient.close();
        }
    }

    @BeforeEach
    void seedUser() throws Exception {
        client.deleteByQuery(d -> d.index(INDEX).query(q -> q.matchAll(m -> m)));
        jdbc.update("DELETE FROM search_outbox");
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,display_name,role) VALUES(?,?,?,'USER')",
                userId, userId + "@example.test", "Learner");
        var topicId = UUID.randomUUID();
        jdbc.update("INSERT INTO topics(id,name,slug) VALUES(?,?,?)",
                topicId, "Java", "java-" + topicId.toString().substring(0, 8));
        moduleId = UUID.randomUUID();
        jdbc.update("INSERT INTO modules(id,topic_id,name,slug) VALUES(?,?,?,?)",
                moduleId, topicId, "JVM", "jvm-" + moduleId.toString().substring(0, 8));
    }

    private UUID insertQuestion(String title, String answer, String searchable) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questions(id,module_id,title,answer_html,difficulty,searchable_text) "
                        + "VALUES(?,?,?,?,'MID',?)",
                id, moduleId, title, answer, searchable);
        return id;
    }

    /** The write path is a trigger, so a plain INSERT must be enough to enqueue indexing. */
    @Test
    void insertEnqueuesADocumentAndTheRelayIndexesIt() {
        UUID id = insertQuestion("What is a heap dump", "<p>heap memory snapshot</p>", "heap memory dump");

        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM search_outbox WHERE processed_at IS NULL", Integer.class),
                "V023 trigger must enqueue the new question");

        relay.drain();

        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM search_outbox WHERE processed_at IS NOT NULL", Integer.class));
        assertTrue(adapter.search(userId, "heap", 10)
                        .stream().anyMatch(hit -> hit.id().equals(id)),
                "indexed question must be findable via the search port");
    }

    /**
     * The regression this design exists to prevent. Index and query must both go through
     * SearchText: the index holds "dong bo" (stopword dropped, diacritics folded), so a user
     * typing the accented phrase must still match, and typing it without diacritics must too.
     */
    @Test
    void vietnameseQueryMatchesTheNormalisedTokensInBothDiacriticForms() {
        UUID id = insertQuestion("Bất đồng bộ trong Java",
                "<p>CompletableFuture</p>",
                SearchText.build("Bất đồng bộ trong Java", "CompletableFuture", "async"));
        relay.drain();

        List<SearchHit> accented = adapter.search(userId, "bất đồng bộ", 10);
        List<SearchHit> plain = adapter.search(userId, "bat dong bo", 10);

        assertTrue(accented.stream().anyMatch(hit -> hit.id().equals(id)), "accents must match");
        assertTrue(plain.stream().anyMatch(hit -> hit.id().equals(id)), "unaccented must match");
    }

    /** A query made only of stopwords must match nothing, not everything. */
    @Test
    void stopwordOnlyQueryReturnsNothing() {
        insertQuestion("Bất đồng bộ trong Java", "<p>x</p>", "bat-dong-bo");
        relay.drain();

        assertTrue(adapter.search(userId, "là gì", 10).isEmpty());
        assertTrue(adapter.search(userId, "what is it", 10).isEmpty());
    }

    /** Notes are owner-scoped: the filter must be applied even when the score is a perfect match. */
    @Test
    void aNoteIsInvisibleToOtherCallers() {
        UUID noteId = UUID.randomUUID();
        jdbc.update("INSERT INTO notes(id,user_id,note_type,content) VALUES(?,?,'QUICK',?)",
                noteId, userId, "private note about heap sizing");
        relay.drain();

        assertTrue(adapter.search(userId, "heap", 10).stream().anyMatch(hit -> hit.id().equals(noteId)),
                "the owner must see their own note");
        assertTrue(adapter.search(UUID.randomUUID(), "heap", 10).isEmpty(),
                "a different caller must never see another user's note");
    }

    /** Only published posts are searchable; archiving must remove an already-indexed post. */
    @Test
    void blogPostIsIndexedOnlyWhilePublished() {
        UUID postId = UUID.randomUUID();
        jdbc.update("INSERT INTO blog_posts(id,author_type,title,slug,body,status) "
                        + "VALUES(?,'AI',?,?,?,'DRAFT')",
                postId, "Draft about indexes", "draft-" + postId.toString().substring(0, 8), "body text");
        relay.drain();
        assertTrue(adapter.search(userId, "indexes", 10).isEmpty(), "a draft must not be searchable");

        jdbc.update("UPDATE blog_posts SET status='PUBLISHED',published_at=NOW() WHERE id=?", postId);
        relay.drain();
        assertTrue(adapter.search(userId, "indexes", 10).stream().anyMatch(hit -> hit.id().equals(postId)),
                "publishing must add the post");

        jdbc.update("UPDATE blog_posts SET status='ARCHIVED' WHERE id=?", postId);
        relay.drain();
        assertTrue(adapter.search(userId, "indexes", 10).stream().noneMatch(hit -> hit.id().equals(postId)),
                "archiving must remove the post");
    }

    /** A deleted row must leave the index, including when the trigger emitted a DELETE event. */
    @Test
    void deletingARowRemovesItFromTheIndex() {
        UUID id = insertQuestion("Temporary question about deadlock", "<p>x</p>", "deadlock");
        relay.drain();
        assertTrue(adapter.search(userId, "deadlock", 10).stream().anyMatch(hit -> hit.id().equals(id)));

        jdbc.update("DELETE FROM questions WHERE id=?", id);
        relay.drain();

        assertTrue(adapter.search(userId, "deadlock", 10).stream().noneMatch(hit -> hit.id().equals(id)));
    }

    /**
     * Several writes before one drain must collapse to a single document in its final state, not
     * be replayed in write order. {@code NOW()} is second-resolution so ordering cannot come from
     * {@code occurred_at}; this is what {@code seq} is for.
     */
    @Test
    void multipleWritesForOneDocumentCollapseToTheLatestState() {
        UUID id = insertQuestion("Original title about sockets", "<p>x</p>", "sockets");
        jdbc.update("UPDATE questions SET title='Renamed title about streams' WHERE id=?", id);
        jdbc.update("UPDATE questions SET title='Final title about buffers' WHERE id=?", id);

        assertEquals(3, jdbc.queryForObject(
                "SELECT count(*) FROM search_outbox WHERE processed_at IS NULL", Integer.class));

        relay.drain();

        List<SearchHit> hits = adapter.search(userId, "buffers", 10);
        assertEquals(1, hits.size(), "one document, not three");
        assertTrue(hits.get(0).title().contains("Final"), "the newest write must win");

        // The superseded rows must be closed out, or they are re-read on every tick forever.
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM search_outbox WHERE processed_at IS NULL", Integer.class));
    }

    /** A failure must leave rows pending with the attempt recorded, so the next tick retries. */
    @Test
    void indexFailureKeepsRowsPendingForRetry() {
        insertQuestion("Question about retries", "<p>x</p>", "retry");
        SearchIndexRelay failing = new SearchIndexRelay(jdbc, documents, new SearchIndexPort() {
            @Override public void index(java.util.Collection<SearchDocument> documents) {
                throw new IllegalStateException("cluster unavailable");
            }
            @Override public void delete(java.util.Collection<SearchIndexPort.DocumentRef> refs) {
                throw new IllegalStateException("cluster unavailable");
            }
        }, tx);

        failing.drain();

        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM search_outbox WHERE processed_at IS NOT NULL", Integer.class),
                "nothing may be marked processed when delivery failed");
        assertEquals(1, jdbc.queryForObject(
                "SELECT attempts FROM search_outbox WHERE processed_at IS NULL", Integer.class));
        assertNotNull(jdbc.queryForObject(
                "SELECT last_error FROM search_outbox WHERE processed_at IS NULL", String.class));

        // The real relay picks the same row up and succeeds.
        relay.drain();
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM search_outbox WHERE processed_at IS NULL", Integer.class));
    }
}
