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
 * - Flyway migrate sạch 15 migration (V001–V013 ở m2, V014–V015 ở m4a)
 * - Đúng 32 bảng trong schema public (loại flyway_schema_history)
 * - 1 materialized view: user_topic_mastery
 * - m4a: unique index `uk_questions_module_sort` + cột `questions.searchable_text` + trigger tsvector
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
    void shouldMigrateAllFifteenMigrations() throws Exception {
        query("SELECT count(*) FROM flyway_schema_history WHERE success = true", rs -> {
            assertEquals(15, rs.getInt(1),
                    "Expected 15 successful Flyway migrations (V001–V015)");
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
    void shouldCreateExactly32Tables() throws Exception {
        query("SELECT count(*) FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' " +
                "AND table_name != 'flyway_schema_history'", rs -> {
            assertEquals(32, rs.getInt(1),
                    "Expected exactly 32 tables excluding flyway_schema_history");
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