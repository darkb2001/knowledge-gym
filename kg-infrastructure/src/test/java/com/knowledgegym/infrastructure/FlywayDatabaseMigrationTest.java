package com.knowledgegym.infrastructure;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Flyway migration integration test — Testcontainers PostgreSQL.
 * Runs on CI (ubuntu-latest has Docker) and locally when Docker daemon is up.
 *
 * Success criteria:
 * - Flyway migrate sạch 23 migration (V022 AI writer schedule/revisions; V023 search outbox)
 * - Đúng 39 bảng trong schema public (loại flyway_schema_history)
 * - 1 materialized view: user_topic_mastery
 * - m4a: unique index `uk_questions_module_sort` + cột `questions.searchable_text` + trigger tsvector
 * - m6a (V016): bảng `quiz_session_questions` + UK `(session_id, question_id)` trên
 *   `quiz_answers`/`interview_answers` — hàng rào chống double-submit ở DB, không phụ thuộc Redis
 * - m6c (V017): `interview_answers.display_order` giữ thứ tự câu đã giao
 */
@Testcontainers
class FlywayDatabaseMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("knowledgegym_test")
                    .withUsername("test")
                    .withPassword("test");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .outOfOrder(false)
                .load()
                .migrate();
    }

    @Test
    void shouldMigrateAllTwentyThreeMigrations() throws Exception {
        query("SELECT count(*) FROM flyway_schema_history WHERE success = true", rs -> {
            assertEquals(23, rs.getInt(1),
                    "Expected 23 successful Flyway migrations (V001–V023)");
        });
    }

    @Test
    void shouldInstallNotesSearchVectorTrigger() throws Exception {
        query("SELECT count(*) FROM information_schema.triggers WHERE trigger_schema='public' " +
                "AND event_object_table='notes' AND trigger_name='trg_notes_search_vector'", rs -> {
            assertEquals(2, rs.getInt(1), "V020 must maintain notes full-text vector on INSERT and UPDATE");
        });
        query("SELECT action_statement FROM information_schema.triggers WHERE trigger_schema='public' " +
                "AND event_object_table='notes' AND trigger_name='trg_notes_search_vector' " +
                "AND event_manipulation='UPDATE' LIMIT 1", rs -> {
            // V020 fires on any UPDATE (no UPDATE OF column list), so updated_at stays correct.
            assertEquals("EXECUTE FUNCTION notes_search_vector_update()", rs.getString(1));
        });
    }

    @Test
    void shouldRequireDisplayOrderOnInterviewAnswers() throws Exception {
        query("SELECT is_nullable FROM information_schema.columns " +
                "WHERE table_schema = 'public' AND table_name = 'interview_answers' " +
                "AND column_name = 'display_order'", rs -> {
            assertEquals("NO", rs.getString(1), "interview_answers.display_order phải NOT NULL (V017)");
        });
    }

    @Test
    void shouldCreateQuizSessionQuestionsAndSessionQuestionUniqueConstraints() throws Exception {
        query("SELECT count(*) FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_name = 'quiz_session_questions'", rs -> {
            assertEquals(1, rs.getInt(1), "quiz_session_questions must exist (V016)");
        });
        for (String constraint : new String[]{
                "uk_quiz_answers_session_question", "uk_interview_answers_session_question"}) {
            query("SELECT count(*) FROM pg_constraint WHERE conname = '" + constraint + "'", rs -> {
                assertEquals(1, rs.getInt(1), constraint + " must exist (V016)");
            });
        }
    }

    @Test
    void shouldCascadeDeleteQuizSessionQuestionsWhenQuestionDeleted() throws Exception {
        // `ON DELETE CASCADE` thay cho `NO ACTION` của V013: re-import xoá câu không được nổ FK.
        query("SELECT confdeltype FROM pg_constraint " +
                "WHERE conname = 'quiz_session_questions_question_id_fkey'", rs -> {
            assertEquals("c", rs.getString(1), "question_id FK phải là ON DELETE CASCADE (V016)");
        });
    }

    @Test
    void shouldCreateUniqueIndexOnModuleSortOrder() throws Exception {
        query("SELECT count(*) FROM pg_indexes " +
                "WHERE schemaname = 'public' AND indexname = 'uk_questions_module_sort'", rs -> {
            assertEquals(1, rs.getInt(1), "uk_questions_module_sort must exist (V014)");
        });
    }

    @Test
    void shouldCreateSearchVectorTriggerAndColumn() throws Exception {
        query("SELECT count(*) FROM information_schema.columns " +
                "WHERE table_schema = 'public' AND table_name = 'questions' " +
                "AND column_name = 'searchable_text'", rs -> {
            assertEquals(1, rs.getInt(1), "questions.searchable_text must exist (V015)");
        });
        query("SELECT count(*) FROM pg_trigger " +
                "WHERE tgname = 'trg_questions_search' AND NOT tgisinternal", rs -> {
            assertEquals(1, rs.getInt(1), "trg_questions_search must exist (V015)");
        });
        query("SELECT count(*) FROM pg_indexes " +
                "WHERE schemaname = 'public' AND indexname = 'idx_questions_search'", rs -> {
            assertEquals(1, rs.getInt(1), "GIN index on search_vector must exist (V003)");
        });
    }

    @Test
    void shouldCreateExactly39Tables() throws Exception {
        query("SELECT count(*) FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' " +
                "AND table_name != 'flyway_schema_history'", rs -> {
            assertEquals(39, rs.getInt(1),
                    "Expected exactly 39 tables excluding flyway_schema_history");
        });
    }

    @Test
    void shouldInstallSearchOutboxCaptureTriggers() throws Exception {
        // V023: every write path that changes what is searchable must enqueue a
        // document, otherwise the ES index drifts from Postgres silently.
        query("SELECT count(*) FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_name = 'search_outbox'", rs -> {
            assertEquals(1, rs.getInt(1), "V023 must create search_outbox");
        });
        for (String table : new String[] {"questions", "notes", "blog_posts"}) {
            query("SELECT count(*) FROM information_schema.triggers WHERE trigger_schema='public' " +
                    "AND event_object_table='" + table + "' AND trigger_name LIKE 'trg_search_outbox_%'", rs -> {
                assertEquals(3, rs.getInt(1),
                        table + " must capture INSERT, UPDATE and DELETE");
            });
        }
        // Backfill: the corpus that already exists when the flag is flipped.
        query("SELECT count(*) FROM search_outbox WHERE processed_at IS NULL", rs -> {
            assertEquals(0, rs.getInt(1), "backfill runs on an empty schema in this test");
        });
    }

    @Test
    void shouldCreateUserTopicMasteryMaterializedView() throws Exception {
        query("SELECT count(*) FROM pg_matviews " +
                "WHERE schemaname = 'public' AND matviewname = 'user_topic_mastery'", rs -> {
            assertEquals(1, rs.getInt(1), "user_topic_mastery MVIEW must exist (V010)");
        });
    }

    @Test
    void shouldCreateTransactionalOutboxAndBlogSearchVector() throws Exception {
        query("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' " +
                "AND table_name='event_outbox'", rs -> assertEquals(1, rs.getInt(1)));
        query("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' " +
                "AND table_name='blog_posts' AND column_name='search_vector'", rs -> assertEquals(1, rs.getInt(1)));
        query("SELECT count(*) FROM collector_sources WHERE active", rs -> assertEquals(2, rs.getInt(1)));
    }

    @Test
    void shouldCreatePersistentWriterSettingsAndRevisionHistory() throws Exception {
        query("SELECT publish_policy || ':' || schedule_enabled::text FROM blog_writer_settings WHERE id", rs ->
                assertEquals("MANUAL_REVIEW:false", rs.getString(1)));
        query("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' " +
                "AND table_name IN ('blog_draft_revisions','blog_writer_audit')", rs -> assertEquals(2, rs.getInt(1)));
        query("SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND indexname='uk_blog_generation_idempotency'", rs -> assertEquals(1, rs.getInt(1)));
    }

    @Test
    void v018BackfillsHistoricalXpAndMasteryFromAttempts() throws Exception {
        String schema = "m7_backfill_test";
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("17")).load().migrate();

        UUID user = UUID.randomUUID();
        UUID module = UUID.randomUUID();
        UUID topic = UUID.randomUUID();
        UUID firstQuestion = UUID.randomUUID();
        UUID secondQuestion = UUID.randomUUID();
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = conn.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            statement.execute("INSERT INTO users (id,email,display_name) VALUES ('" + user +
                    "','m7-backfill@example.com','Backfill')");
            statement.execute("INSERT INTO topics (id,name,slug) VALUES ('" + topic + "','Topic','topic')");
            statement.execute("INSERT INTO modules (id,topic_id,name,slug) VALUES ('" + module +
                    "','" + topic + "','Module','module')");
            statement.execute("INSERT INTO questions (id,module_id,title,answer_html,difficulty,sort_order) VALUES " +
                    "('" + firstQuestion + "','" + module + "','Q1','A','MID',1)," +
                    "('" + secondQuestion + "','" + module + "','Q2','A','MID',2)");
            Instant first = Instant.parse("2026-01-01T00:00:00Z");
            statement.execute("INSERT INTO study_attempts (user_id,question_id,source,is_correct,attempted_at) VALUES " +
                    "('" + user + "','" + firstQuestion + "','FLASHCARD',true,'" + first + "')," +
                    "('" + user + "','" + firstQuestion + "','PRACTICE',false,'" + first.plusSeconds(1) + "')," +
                    "('" + user + "','" + secondQuestion + "','DAILY',false,'" + first.plusSeconds(2) + "')");
        }

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate();

        queryInSchema(schema, "SELECT xp FROM users WHERE id = '" + user + "'", rs ->
                assertEquals(11, rs.getInt(1), "first attempt per question: 10 XP + 1 XP"));
        queryInSchema(schema, "SELECT total_attempts,correct_count,mastery_pct,streak_days FROM user_progress " +
                "WHERE user_id = '" + user + "' AND module_id = '" + module + "'", rs -> {
            assertEquals(3, rs.getInt(1));
            assertEquals(1, rs.getInt(2));
            assertEquals("33.33", rs.getBigDecimal(3).toPlainString());
            assertEquals(0, rs.getInt(4), "M7 derives streak on read");
        });
    }

    private interface RowAssert {
        void accept(ResultSet rs) throws Exception;
    }

    private void query(String sql, RowAssert assertion) throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            assertion.accept(rs);
        }
    }

    private void queryInSchema(String schema, String sql, RowAssert assertion) throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("SET search_path TO " + schema);
            try (ResultSet rs = stmt.executeQuery(sql)) {
                rs.next();
                assertion.accept(rs);
            }
        }
    }
}
