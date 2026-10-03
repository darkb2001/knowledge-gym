package com.knowledgegym.infrastructure.security;

import com.knowledgegym.identity.domain.port.EmailVerificationPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Atomic single-use claim and failed-attempt accounting, persisted even on HTTP 400. */
@Component
public class JdbcEmailVerificationAdapter implements EmailVerificationPort {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public JdbcEmailVerificationAdapter(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public boolean issue(String email, String hash) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            jdbc.update("DELETE FROM registration_email_challenges WHERE expires_at < now() - interval '1 day'");
            return jdbc.update("""
                INSERT INTO registration_email_challenges(email, code_hash, expires_at)
                VALUES (?, ?, now() + interval '10 minutes')
                ON CONFLICT (email) DO UPDATE SET code_hash = EXCLUDED.code_hash,
                    created_at = now(), expires_at = EXCLUDED.expires_at, attempts = 0, consumed = false
                WHERE registration_email_challenges.created_at <= now() - interval '1 minute'
                """, email, hash) == 1;
        }));
    }

    @Override
    public boolean consume(String email, String hash) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            var rows = jdbc.queryForList("""
                SELECT code_hash FROM registration_email_challenges
                WHERE email = ? AND expires_at > now() AND NOT consumed AND attempts < 5
                FOR UPDATE
                """, String.class, email);
            if (rows.isEmpty()) return false;
            if (!rows.getFirst().equals(hash)) {
                jdbc.update("UPDATE registration_email_challenges SET attempts = attempts + 1 WHERE email = ?", email);
                return false;
            }
            jdbc.update("UPDATE registration_email_challenges SET consumed = true WHERE email = ?", email);
            return true;
        }));
    }

    @Override
    public void invalidate(String email, String hash) {
        jdbc.update("DELETE FROM registration_email_challenges WHERE email = ? AND code_hash = ?", email, hash);
    }
}
