package com.knowledgegym.infrastructure.security;

import com.knowledgegym.shared.domain.model.UserRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Authoritative account state for each request; stale JWT roles cannot retain privileges. */
@Component
public class JdbcAccessTokenGuard {
    private final JdbcTemplate jdbc;
    public JdbcAccessTokenGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<UserRole> authorizedRole(UUID id, Instant issuedAt) {
        if (issuedAt == null) return Optional.empty();
        return jdbc.query("SELECT role,blocked,tokens_invalid_before FROM users WHERE id=?", (rs, n) -> {
            var cutoff = rs.getTimestamp("tokens_invalid_before");
            if (rs.getBoolean("blocked") || cutoff != null && !issuedAt.isAfter(cutoff.toInstant()))
                return Optional.<UserRole>empty();
            return Optional.of(UserRole.valueOf(rs.getString("role")));
        }, id).stream().findFirst().orElse(Optional.empty());
    }
}
