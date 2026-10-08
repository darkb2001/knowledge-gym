package com.knowledgegym.infrastructure.english;

import com.knowledgegym.english.application.EnglishCatalog;
import com.knowledgegym.english.application.EnglishPracticeUseCase;
import com.knowledgegym.infrastructure.TestPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;

class EnglishPracticePersistenceTest {
    @Test void realMigrationsOwnershipDraftResumeOptimisticWritesAndFinalHistory() throws Exception {
        try (var db = new TestPostgres()) {
            db.start();
            var ds = db.dataSource();
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
            var jdbc = new JdbcTemplate(ds);
            UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
            for (UUID user : new UUID[]{owner, other})
                jdbc.update("INSERT INTO users(id,email,display_name) VALUES(?,?,?)", user, user + "@example.test", "English learner");
            var repo = new JdbcEnglishAttemptRepository(jdbc, JsonMapper.builder().build());
            var service = new EnglishPracticeUseCase(repo, new EnglishCatalog());
            var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
            var draft = tx.execute(status -> service.start(owner, "writing-email-v1"));
            assertThat(tx.execute(status -> service.start(owner, "writing-email-v1")).id()).isEqualTo(draft.id());
            assertThat(repo.findOwned(other, draft.id())).isEmpty();
            assertThat(repo.listOwned(other, 1, 10).items()).isEmpty();
            String text = "Dear Alex, I'd love to show you our town. <script>not rendered as HTML</script>";
            var saved = service.save(owner, draft.id(), 0, Map.of(), text, 90, false);
            assertThat(repo.findOwned(owner, draft.id()).orElseThrow().response()).isEqualTo(text);
            assertThat(repo.updateOwned(saved, 0)).isFalse();
            service.save(owner, draft.id(), 1, Map.of(), text, 120, true);
            assertThat(repo.findOwned(owner, draft.id()).orElseThrow().status()).isEqualTo("SUBMITTED");
            assertThat(repo.updateOwned(saved, 2)).isFalse();
            var second = tx.execute(status -> service.start(owner, "writing-email-v1"));
            assertThat(second.id()).isNotEqualTo(draft.id());
            assertThat(repo.listOwned(owner, 1, 1).totalElements()).isEqualTo(2);
            assertThat(repo.listOwned(owner, 2, 1).items()).hasSize(1);
            // Concurrent start requests return the same draft under the real per-owner DB lock.
            try (var pool = Executors.newFixedThreadPool(2)) {
                var a = pool.submit(() -> tx.execute(status -> service.start(owner, "listening-announcement-v1")));
                var b = pool.submit(() -> tx.execute(status -> service.start(owner, "listening-announcement-v1")));
                assertThat(a.get().id()).isEqualTo(b.get().id());
            }
            var listening = tx.execute(status -> service.start(owner, "listening-announcement-v1"));
            service.save(owner, listening.id(), 0, Map.of("q1", 1, "q2", 2, "q3", 0), "", 10, true);
            assertThat(repo.findOwned(owner, listening.id()).orElseThrow().answers()).containsEntry("q2", 2);
            assertThat(jdbc.queryForObject("SELECT xp FROM users WHERE id=?", Integer.class, owner)).isZero();
        }
    }
}
