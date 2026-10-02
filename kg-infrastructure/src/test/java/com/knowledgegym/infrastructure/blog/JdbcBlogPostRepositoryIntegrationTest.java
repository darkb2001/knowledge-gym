package com.knowledgegym.infrastructure.blog;

import tools.jackson.databind.ObjectMapper;
import com.knowledgegym.blog.domain.model.CollectedItem;
import com.knowledgegym.blog.domain.port.BlogScheduleSettingsPort;
import com.knowledgegym.blog.domain.port.AiWriterPort;
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
    static JdbcBlogScheduleSettingsAdapter scheduleSettings;
    static JdbcBlogWriterRepository writerRepository;
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
        scheduleSettings = new JdbcBlogScheduleSettingsAdapter(jdbc,repository);
        writerRepository = new JdbcBlogWriterRepository(jdbc,new ObjectMapper());
    }

    @BeforeEach
    void createUser() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,display_name,role) VALUES(?,?,?,'ADMIN')",
                userId, userId + "@example.test", "Blog author");
        jdbc.update("UPDATE blog_writer_settings SET schedule_enabled=FALSE,local_time='06:00',timezone='Asia/Jakarta',daily_limit=1,publish_policy='MANUAL_REVIEW',quality_threshold=85,last_scheduled_date=NULL,updated_by=NULL WHERE id=TRUE");
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
                "SELECT count(*) FROM event_outbox WHERE event_type='collector.item_collected.v1' AND aggregate_id=(SELECT id FROM collected_items WHERE url=?) AND payload->>'sourceId'=?",
                Integer.class, url, sourceId.toString()));
    }

    @Test
    void scheduleAndPolicyPersistIndependentlyAndDailyEnqueueIsIdempotent() {
        var disabled = scheduleSettings.current();
        assertFalse(disabled.enabled());
        assertEquals(BlogScheduleSettingsPort.PublishPolicy.MANUAL_REVIEW, disabled.policy());
        scheduleSettings.update(false, java.time.LocalTime.of(7, 15), java.time.ZoneId.of("Asia/Jakarta"), 2,
                BlogScheduleSettingsPort.PublishPolicy.AUTO_PUBLISH_QUALIFIED, 88, userId);
        assertTrue(scheduleSettings.withinGlobalLimits(100, 1_000_000, 100d));

        var date = java.time.LocalDate.of(2035, 2, 3);
        assertFalse(scheduleSettings.enqueueScheduled(date, java.time.Instant.now(), 2));
        scheduleSettings.update(true, java.time.LocalTime.of(7, 15), java.time.ZoneId.of("Asia/Jakarta"), 2,
                BlogScheduleSettingsPort.PublishPolicy.AUTO_PUBLISH_QUALIFIED, 88, userId);
        assertTrue(scheduleSettings.enqueueScheduled(date, java.time.Instant.now(), 2));
        assertFalse(scheduleSettings.enqueueScheduled(date, java.time.Instant.now(), 2));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM blog_generation_queue WHERE idempotency_key LIKE ?", Integer.class, "schedule:" + date + ":%"));
        var enabled = scheduleSettings.current();
        assertTrue(enabled.enabled());
        assertEquals(BlogScheduleSettingsPort.PublishPolicy.AUTO_PUBLISH_QUALIFIED, enabled.policy());
    }

    @Test
    void generatedDraftPersistsGroundedSourcesAndImmutableRevisionHistory() {
        UUID sourceId=jdbc.queryForObject("SELECT id FROM collector_sources ORDER BY name LIMIT 1",UUID.class);
        String url="https://example.test/ai-source/"+UUID.randomUUID();
        collectorRepository.saveCollected(sourceId,List.of(new CollectedItem("Spring transaction guide",url,"Evidence summary","f".repeat(64),java.time.Instant.now(),List.of("spring"),90,"spring")));
        UUID itemId=jdbc.queryForObject("SELECT id FROM collected_items WHERE url=?",UUID.class,url);
        String body="<h2>Why</h2><p>"+"Detailed sourced text. ".repeat(30)+"</p><h2>How</h2><pre><code>example</code></pre>";
        var generated=new AiWriterPort.Completion("Spring Transactions Explained",body,"A guide grounded in collected evidence.","Spring Transactions","Transaction guide",List.of("spring"),List.of(itemId),"gpt-4o-mini",100,500,0.0003);

        var post=writerRepository.createReviewDraft(generated,List.of(itemId),82,UUID.randomUUID());

        assertEquals("REVIEW",post.status());
        assertEquals(1,writerRepository.revisions(post.id()).size());
        assertEquals(itemId,writerRepository.revisions(post.id()).getFirst().sourceIds().getFirst());
        assertEquals("Spring transaction guide",writerRepository.findSources(List.of(itemId)).getFirst().title());
        assertEquals(post.id(),jdbc.queryForObject("SELECT used_in_post_id FROM collected_items WHERE id=?",UUID.class,itemId));

        var revised=new AiWriterPort.Completion("Spring Transactions Explained Clearly",body,"A revised guide.","Spring Transactions","Transaction guide",List.of("spring"),List.of(itemId),"gpt-4o-mini",120,550,0.00035);
        writerRepository.appendRevision(post.id(),revised,List.of(itemId),"Add a clearer explanation",86,userId);
        assertEquals(2,writerRepository.revisions(post.id()).size());
        assertEquals("Spring Transactions Explained Clearly",writerRepository.findPost(post.id()).orElseThrow().title());
        writerRepository.restoreRevision(post.id(),1);
        assertEquals("Spring Transactions Explained",writerRepository.findPost(post.id()).orElseThrow().title());
        assertEquals("Spring Transactions",jdbc.queryForObject("SELECT seo_title FROM blog_posts WHERE id=?",String.class,post.id()));
    }

    @Test
    void globalBudgetReservationIsAtomicIdempotentAndSettledWithActualUsage() {
        jdbc.update("DELETE FROM blog_ai_budget_reservations");
        UUID first=UUID.randomUUID(),second=UUID.randomUUID(),third=UUID.randomUUID();
        assertTrue(Boolean.TRUE.equals(tx.execute(status ->
                scheduleSettings.reserveGlobalBudget(first,2,16_000,0.02,8_000,0.01))));
        assertFalse(Boolean.TRUE.equals(tx.execute(status ->
                scheduleSettings.reserveGlobalBudget(first,2,16_000,0.02,8_000,0.01))));
        assertTrue(Boolean.TRUE.equals(tx.execute(status ->
                scheduleSettings.reserveGlobalBudget(second,2,16_000,0.02,8_000,0.01))));
        assertFalse(Boolean.TRUE.equals(tx.execute(status ->
                scheduleSettings.reserveGlobalBudget(third,2,16_000,0.02,8_000,0.01))));
        tx.executeWithoutResult(status -> scheduleSettings.settleGlobalBudget(first,3_000,0.001));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM blog_ai_budget_reservations WHERE request_id=? AND finalized AND tokens_used=3000",Integer.class,first));
        assertNotNull(writerRepository.stats());
    }

    @Test
    void manualEnqueueIsIdempotentAndProviderFailuresHaveBoundedVisibleRetries() {
        jdbc.update("UPDATE blog_generation_queue SET status='DONE',completed_at=NOW() WHERE status IN ('QUEUED','GENERATING')");
        UUID queueId=scheduleSettings.enqueueNow("Retry test",userId,"retry-test-key");
        assertEquals(queueId,scheduleSettings.enqueueNow("Retry test",userId,"retry-test-key"));
        for(int attempt=1;attempt<=3;attempt++){
            var job=tx.execute(status -> scheduleSettings.claimNext());
            assertNotNull(job);
            assertEquals(queueId,job.id());
            assertEquals(attempt,job.attempts());
            UUID run=writerRepository.startRun(queueId,"retry test attempt "+attempt);
            tx.executeWithoutResult(status -> scheduleSettings.fail(queueId,run,"temporary provider error"));
            if(attempt<3)jdbc.update("UPDATE blog_generation_queue SET retry_after=NOW()-INTERVAL '1 second' WHERE id=?",queueId);
        }
        var exhausted=scheduleSettings.findJob(queueId);
        assertEquals("FAILED",exhausted.status());
        assertEquals("temporary provider error",exhausted.errorMessage());
        assertEquals(3,exhausted.attempts());
    }

    @Test
    void autoPublishRereadsPolicyUnderSettingsRowLock(){
        UUID collectorSource=jdbc.queryForObject("SELECT id FROM collector_sources ORDER BY name LIMIT 1",UUID.class);
        String url="https://example.test/policy/"+UUID.randomUUID();
        collectorRepository.saveCollected(collectorSource,List.of(new CollectedItem("Policy test source",url,"Grounding","e".repeat(64),java.time.Instant.now(),List.of(),80,"java")));
        UUID itemId=jdbc.queryForObject("SELECT id FROM collected_items WHERE url=?",UUID.class,url);
        String body="<h2>Evidence</h2><p>"+"Supported article content. ".repeat(20)+"</p>";
        var completion=new AiWriterPort.Completion("A sufficiently clear policy test article",body,"A sourced test excerpt",null,null,List.of(),List.of(itemId),"test",1,1,0.001);
        var draft=writerRepository.createReviewDraft(completion,List.of(itemId),90,UUID.randomUUID());

        scheduleSettings.update(false,java.time.LocalTime.NOON,java.time.ZoneId.of("Asia/Jakarta"),1,
                BlogScheduleSettingsPort.PublishPolicy.MANUAL_REVIEW,80,userId);
        assertFalse(Boolean.TRUE.equals(tx.execute(status->scheduleSettings.publishIfStillQualified(draft.id(),90))));
        assertEquals("REVIEW",writerRepository.findPost(draft.id()).orElseThrow().status());

        scheduleSettings.update(false,java.time.LocalTime.NOON,java.time.ZoneId.of("Asia/Jakarta"),1,
                BlogScheduleSettingsPort.PublishPolicy.AUTO_PUBLISH_QUALIFIED,80,userId);
        assertTrue(Boolean.TRUE.equals(tx.execute(status->scheduleSettings.publishIfStillQualified(draft.id(),90))));
        assertEquals("PUBLISHED",writerRepository.findPost(draft.id()).orElseThrow().status());
        scheduleSettings.update(false,java.time.LocalTime.of(6,0),java.time.ZoneId.of("Asia/Jakarta"),1,
                BlogScheduleSettingsPort.PublishPolicy.MANUAL_REVIEW,85,userId);
    }

    @Test
    void reclaimStaleGeneratingCompletesDraftedJobsAndRequeuesIncompleteOnes() {
        jdbc.update("UPDATE blog_generation_queue SET status='DONE',completed_at=NOW() WHERE status IN ('QUEUED','GENERATING')");
        UUID withDraft = scheduleSettings.enqueueNow("Recover drafted", userId, "reclaim-with-draft");
        UUID withoutDraft = scheduleSettings.enqueueNow("Recover incomplete", userId, "reclaim-without-draft");
        UUID postId = UUID.randomUUID();
        jdbc.update("INSERT INTO blog_posts(id,author_id,author_type,title,slug,body,status,tags) VALUES(?,NULL,'AI','Recovered','recovered-" + postId + "','<p>body</p>','REVIEW','{}')",
                postId);
        jdbc.update("UPDATE blog_generation_queue SET status='GENERATING',started_at=NOW()-INTERVAL '20 minutes',generated_post_id=? WHERE id=?",
                postId, withDraft);
        jdbc.update("UPDATE blog_generation_queue SET status='GENERATING',started_at=NOW()-INTERVAL '20 minutes' WHERE id=?",
                withoutDraft);

        int changed = tx.execute(status -> scheduleSettings.reclaimStaleGenerating(java.time.Duration.ofMinutes(15)));
        assertTrue(changed >= 2);
        assertEquals("DONE", scheduleSettings.findJob(withDraft).status());
        assertEquals(postId, scheduleSettings.findJob(withDraft).generatedPostId());
        assertEquals("QUEUED", scheduleSettings.findJob(withoutDraft).status());
    }

    @Test
    void releaseUnsettledBudgetClearsPhantomReservation() {
        jdbc.update("DELETE FROM blog_ai_budget_reservations");
        UUID request = UUID.randomUUID();
        assertTrue(Boolean.TRUE.equals(tx.execute(status ->
                scheduleSettings.reserveGlobalBudget(request, 10, 100_000, 1.0, 8_000, 0.01))));
        tx.executeWithoutResult(status -> scheduleSettings.releaseUnsettledBudget(request));
        assertEquals(0, jdbc.queryForObject(
                "SELECT tokens_used FROM blog_ai_budget_reservations WHERE request_id=? AND finalized",
                Integer.class, request));
        assertTrue(Boolean.TRUE.equals(tx.execute(status ->
                scheduleSettings.reserveGlobalBudget(UUID.randomUUID(), 10, 100_000, 1.0, 8_000, 0.01))));
    }
}
