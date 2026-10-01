package com.knowledgegym.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Flyway migration integration test — Testcontainers PostgreSQL.
 * Runs on CI (ubuntu-latest has Docker) and locally when Docker daemon is up.
 *
 * Success criteria:
 * - Flyway migrate sạch 17 migration (V001–V013 ở m2, V014–V015 ở m4a, V016 ở m6a, V017 ở m6c)
 * - Đúng 33 bảng trong schema public (loại flyway_schema_history)
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
    void shouldMigrateAllSeventeenMigrations() throws Exception {
        query("SELECT count(*) FROM flyway_schema_history WHERE success = true", rs -> {
            assertEquals(17, rs.getInt(1),
                    "Expected 17 successful Flyway migrations (V001–V017)");
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
    void shouldCreateExactly33Tables() throws Exception {
        query("SELECT count(*) FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' " +
                "AND table_name != 'flyway_schema_history'", rs -> {
            assertEquals(33, rs.getInt(1),
                    "Expected exactly 33 tables excluding flyway_schema_history");
        });
    }

    @Test
    void shouldCreateUserTopicMasteryMaterializedView() throws Exception {
        query("SELECT count(*) FROM pg_matviews " +
                "WHERE schemaname = 'public' AND matviewname = 'user_topic_mastery'", rs -> {
            assertEquals(1, rs.getInt(1), "user_topic_mastery MVIEW must exist (V010)");
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
}