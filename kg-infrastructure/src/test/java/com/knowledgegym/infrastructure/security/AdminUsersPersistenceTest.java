package com.knowledgegym.infrastructure.security;

import com.knowledgegym.identity.application.ManageUsersUseCase;
import com.knowledgegym.shared.domain.model.UserRole;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AdminUsersPersistenceTest {
    static final com.knowledgegym.infrastructure.TestPostgres DB = new com.knowledgegym.infrastructure.TestPostgres();
    @org.junit.jupiter.api.AfterAll static void stop() { DB.close(); }
    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private JdbcAdminUserAdapter adapter;
    private JdbcAccessTokenGuard guard;
    private UUID actor, user;

    @BeforeAll static void migrate() {
        DB.start();
        var ds = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    }
    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE users CASCADE");
        actor = UUID.randomUUID(); user = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,display_name,role,email_verified) VALUES (?,?,'Owner','ADMIN',true)",
                actor, "owner@example.com");
        jdbc.update("INSERT INTO users(id,email,display_name,email_verified) VALUES (?,?,'Learner',true)",
                user, "learner@example.com");
        adapter = new JdbcAdminUserAdapter(new NamedParameterJdbcTemplate(jdbc));
        guard = new JdbcAccessTokenGuard(jdbc);
    }
    @Test void blockRevokesTokensPersistsReasonAndUnblockDoesNotReviveOldAccessToken() {
        var oldIssuedAt = Instant.now().minusSeconds(60);
        jdbc.update("INSERT INTO refresh_tokens(user_id,token_hash,family_id,expires_at) VALUES (?,?,?,now()+interval '1 day')",
                user, "a".repeat(64), UUID.randomUUID());
        var service = new ManageUsersUseCase(adapter);
        tx.executeWithoutResult(s -> service.setBlocked(actor, user, true, "Abuse report"));
        assertTrue(adapter.get(user).blocked());
        assertTrue(guard.authorizedRole(user, Instant.now().plusSeconds(1)).isEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class));
        assertEquals("Abuse report", jdbc.queryForObject(
                "SELECT details->>'reason' FROM audit_logs WHERE entity_id=? ORDER BY created_at DESC LIMIT 1", String.class, user));
        tx.executeWithoutResult(s -> service.setBlocked(actor, user, false, "Appeal"));
        assertTrue(guard.authorizedRole(user, oldIssuedAt).isEmpty());
        assertEquals(UserRole.USER, guard.authorizedRole(user, Instant.now().plusSeconds(1)).orElseThrow());
    }
    @Test void roleChangesUseDatabaseAuthorityAndRollbackIsAtomic() {
        var service = new ManageUsersUseCase(adapter);
        var issuedAt = Instant.now().minusSeconds(10);
        tx.executeWithoutResult(s -> {
            service.changeRole(actor, user, UserRole.ADMIN, "Promotion");
            s.setRollbackOnly();
        });
        assertEquals(UserRole.USER, adapter.get(user).role());
        assertEquals(UserRole.USER, guard.authorizedRole(user, issuedAt).orElseThrow());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=?", Integer.class, user));
        tx.executeWithoutResult(s -> service.changeRole(actor, user, UserRole.PREMIUM, "Subscription"));
        assertTrue(guard.authorizedRole(user, issuedAt).isEmpty());
        assertEquals(UserRole.PREMIUM, guard.authorizedRole(user, Instant.now().plusSeconds(1)).orElseThrow());
    }
    @Test void directoryPaginationAndLiteralSearch() {
        assertEquals(2, adapter.list(null, null, null, 1, 1).totalElements());
        assertEquals(1, adapter.list(null, null, null, 2, 1).items().size());
        assertEquals(1, adapter.list("LEARNER", UserRole.USER, false, 1, 20).totalElements());
        assertEquals(0, adapter.list("%", null, null, 1, 20).totalElements());
        assertEquals(1, adapter.activeAdminCount());
    }
    @Test void concurrentAdminsCannotRemoveEachOther() throws Exception {
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", user);
        var service = new ManageUsersUseCase(adapter);
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { gate.await(); return attemptRemoval(service, actor, user); });
            var b = executor.submit(() -> { gate.await(); return attemptRemoval(service, user, actor); });
            gate.countDown();
            int successes = (a.get(10, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0)
                    + (b.get(10, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(1, adapter.activeAdminCount());
        }
    }
    private boolean attemptRemoval(ManageUsersUseCase service, UUID actorId, UUID targetId) {
        try {
            tx.executeWithoutResult(s -> service.changeRole(actorId, targetId, UserRole.USER, "Policy"));
            return true;
        } catch (com.knowledgegym.identity.application.AuthException
                 | com.knowledgegym.shared.application.ConflictException expected) { return false; }
    }
    @Test void missingUserOrIssuedAtNeverAuthenticates() {
        assertTrue(guard.authorizedRole(UUID.randomUUID(), Instant.now()).isEmpty());
        assertTrue(guard.authorizedRole(user, null).isEmpty());
    }
}
