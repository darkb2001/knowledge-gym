package com.knowledgegym.infrastructure;

import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Explicit local test database opt-in; default remains an isolated Docker PostgreSQL. */
public final class TestPostgres implements AutoCloseable {
    private PostgreSQLContainer<?> container;
    private String url, username, password, provisioningUrl, databaseName;
    public void start() {
        url = System.getenv("KG_TEST_JDBC_URL");
        if (url == null || url.isBlank()) {
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
            url = container.getJdbcUrl(); username = container.getUsername(); password = container.getPassword();
        } else {
            // Tests truncate tables. Refuse arbitrary/production hosts and databases.
            if (!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/kg_test_[a-z0-9_]+"))
                throw new IllegalArgumentException("KG_TEST_JDBC_URL must be an explicit loopback kg_test_* database");
            username = System.getenv("KG_TEST_DB_USER");
            password = System.getenv().getOrDefault("KG_TEST_DB_PASSWORD", "");
            if (username == null || username.isBlank()) throw new IllegalArgumentException("KG_TEST_DB_USER required");
            provisioningUrl = url;
            databaseName = "kg_test_" + java.util.UUID.randomUUID().toString().replace("-", "");
            executeProvisioning("CREATE DATABASE " + databaseName);
            url = provisioningUrl.substring(0, provisioningUrl.lastIndexOf('/') + 1) + databaseName;
        }
    }
    public String getJdbcUrl() { return url; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public DriverManagerDataSource dataSource() { return new DriverManagerDataSource(url, username, password); }
    private void executeProvisioning(String sql) {
        try (var connection = java.sql.DriverManager.getConnection(provisioningUrl, username, password);
             var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (java.sql.SQLException e) { throw new IllegalStateException("Cannot provision isolated test database", e); }
    }
    @Override public void close() {
        if (container != null) container.stop();
        if (databaseName != null) executeProvisioning("DROP DATABASE IF EXISTS " + databaseName + " WITH (FORCE)");
    }
}
