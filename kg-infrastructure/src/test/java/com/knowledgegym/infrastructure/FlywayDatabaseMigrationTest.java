package com.knowledgegym.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
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
 * Flyway migration integration test — Testcontainers PostgreSQL, chạy Flyway programmatic.
 * BẬT: chạy trên local với Docker daemon running (postgres:16-alpine auto-pull).
 * CI: vô hiệu hóa nếu Docker daemon không available.
 *
 * Success criteria (mini-phase 2):
 * - Flyway migrate sạch 13 migration (V001–V013)
 * - Đúng 32 bảng trong schema public (loại flyway_schema_history)
 * - 1 materialized view: user_topic_mastery
 */
@Testcontainers
@Disabled("requires running Docker daemon — enable locally to verify Flyway migrations")
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
    void shouldMigrateAllThirteenMigrations() throws Exception {
        query("SELECT count(*) FROM flyway_schema_history WHERE success = true", rs -> {
            assertEquals(13, rs.getInt(1),
                    "Expected 13 successful Flyway migrations (V001–V013)");
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