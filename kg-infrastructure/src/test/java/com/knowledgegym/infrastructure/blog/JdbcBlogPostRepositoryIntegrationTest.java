package com.knowledgegym.infrastructure.blog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegym.blog.domain.model.CollectedItem;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class JdbcBlogPostRepositoryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("blog_test").withUsername("test").withPassword("test");

    static JdbcTemplate jdbc;
    static JdbcBlogPostRepository repository;
    static JdbcCollectorRepository collectorRepository;
    static TransactionTemplate tx;
    UUID userId;

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        repository = new JdbcBlogPostRepository(jdbc, new ObjectMapper(), "http://localhost:3000");
        collectorRepository = new JdbcCollectorRepository(jdbc, new ObjectMapper());
    }

    @BeforeEach
    void createUser() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,display_name,role) VALUES(?,?,?,'ADMIN')",
                userId, userId + "@example.test", "Blog author");
    }

    @Test
    void publishWritesOutboxAndMakesPostSearchableAndVisible() {
        var draft = tx.execute(status -> repository.createDraft(userId, "Transactional outbox in Spring",
                "transactional-outbox-spring",
                "A searchable blog post about Spring Kafka and PostgreSQL.", "Outbox pattern", null, null,
                List.of("spring", "kafka")));
        assertEquals("DRAFT", draft.status());

        tx.executeWithoutResult(status -> repository.publish(draft.id()));

        var published = repository.findPublishedBySlug(draft.slug()).orElseThrow();
        assertEquals("PUBLISHED", published.status());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM event_outbox WHERE aggregate_id=? AND event_type='blog.published.v1'",
                Integer.class, draft.id()));
        assertEquals("http://localhost:3000/blog/" + draft.slug(),
                jdbc.queryForObject(
                        "SELECT payload->>'canonicalUrl' FROM event_outbox WHERE aggregate_id=? AND event_type='blog.published.v1'",
                        String.class, draft.id()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM blog_posts WHERE id=? AND search_vector @@ plainto_tsquery('simple','Kafka')",
                Integer.class, draft.id()));
    }

    @Test
    void likesAndViewsAreIdempotentPerUserAndPost() {
        var draft = tx.execute(status -> repository.createDraft(userId, "Idempotency", "idempotency-" + userId,
                "Body", "Excerpt", null, null, List.of()));

        assertTrue(Boolean.TRUE.equals(tx.execute(status -> repository.setLiked(draft.id(), userId, true))));
        assertTrue(Boolean.TRUE.equals(tx.execute(status -> repository.setLiked(draft.id(), userId, true))));
        tx.executeWithoutResult(status -> repository.recordView(draft.id(), userId));
        tx.executeWithoutResult(status -> repository.recordView(draft.id(), userId));
        assertEquals(1, jdbc.queryForObject("SELECT like_count FROM blog_posts WHERE id=?", Integer.class, draft.id()));
        assertEquals(1, jdbc.queryForObject("SELECT view_count FROM blog_posts WHERE id=?", Integer.class, draft.id()));

        assertFalse(Boolean.TRUE.equals(tx.execute(status -> repository.setLiked(draft.id(), userId, false))));
        assertFalse(Boolean.TRUE.equals(tx.execute(status -> repository.setLiked(draft.id(), userId, false))));
        assertEquals(0, jdbc.queryForObject("SELECT like_count FROM blog_posts WHERE id=?", Integer.class, draft.id()));
    }

    @Test
    void collectorPersistsTagsAndOutboxOnceAcrossRedelivery() {
        UUID sourceId = jdbc.queryForObject("SELECT id FROM collector_sources ORDER BY name LIMIT 1", UUID.class);
        String url = "https://example.test/release/" + UUID.randomUUID();
        var item = new CollectedItem("Java release", url,
                "Spring and Kafka updates", "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899",
                java.time.Instant.now(), List.of("java", "release"), 75, "spring");

        // Call repository directly twice — use-case interval gating would skip the second fetch.
        Integer first = tx.execute(status -> collectorRepository.saveCollected(sourceId, List.of(item)));
        Integer second = tx.execute(status -> collectorRepository.saveCollected(sourceId, List.of(item)));

        assertEquals(1, first);
        assertEquals(0, second);
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM collected_items WHERE url=? AND tags @> ARRAY['java','release']::text[]",
                Integer.class, url));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM event_outbox WHERE event_type='collector.item_collected.v1' AND payload->>'sourceId'=?",
                Integer.class, sourceId.toString()));
    }
}
