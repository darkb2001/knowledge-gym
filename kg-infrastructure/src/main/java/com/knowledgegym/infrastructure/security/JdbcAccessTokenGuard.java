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

    /**
     * @param issuedAtPreciseToMillis true khi mốc phát hành lấy từ claim {@code iatMs} (chính xác
     *   tới mili-giây) — khi đó token phát ĐÚNG lúc logout cũng bị cắt. Token cũ chỉ có {@code iat}
     *   (độ phân giải giây) được nới một giây, nếu không thì thiết bị khác refresh ngay sau khi
     *   thiết bị này logout sẽ nhận access token vừa phát mà đã 401.
     */
    public Optional<UserRole> authorizedRole(UUID id, Instant issuedAt, boolean issuedAtPreciseToMillis) {
        if (issuedAt == null) return Optional.empty();
        return jdbc.query("SELECT role,blocked,tokens_invalid_before FROM users WHERE id=?", (rs, n) -> {
            var cutoff = rs.getTimestamp("tokens_invalid_before");
            if (rs.getBoolean("blocked") || cutoff != null && isRevoked(issuedAt, cutoff.toInstant(), issuedAtPreciseToMillis))
                return Optional.<UserRole>empty();
            return Optional.of(UserRole.valueOf(rs.getString("role")));
        }, id).stream().findFirst().orElse(Optional.empty());
    }

    /** Token cũ (không có iatMs) dùng luật nới một giây; xem javadoc ở trên. */
    public Optional<UserRole> authorizedRole(UUID id, Instant issuedAt) {
        return authorizedRole(id, issuedAt, false);
    }

    private static boolean isRevoked(Instant issuedAt, Instant cutoff, boolean precise) {
        if (precise) return !issuedAt.isAfter(cutoff);
        return issuedAt.plusSeconds(1).isBefore(cutoff);
    }
}
