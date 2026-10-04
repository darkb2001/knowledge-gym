package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.identity.domain.port.SessionRegistryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Đọc phiên đăng nhập từ bảng audit refresh_tokens: gom theo family_id (1 family = 1 thiết bị).
 * created_at = lần đầu của family (đăng nhập), last_seen_at = lần rotate gần nhất (còn hoạt động).
 */
@Component
public class JdbcSessionRegistryAdapter implements SessionRegistryPort {

    private static final String LIST_ACTIVE = """
            SELECT family_id,
                   MIN(created_at) AS created_at,
                   MAX(created_at) AS last_seen_at,
                   (ARRAY_AGG(ip_address ORDER BY created_at DESC))[1]  AS ip_address,
                   (ARRAY_AGG(user_agent ORDER BY created_at DESC))[1]  AS user_agent
              FROM refresh_tokens
             WHERE user_id = ? AND revoked_at IS NULL AND expires_at > now()
             GROUP BY family_id
             ORDER BY MAX(created_at) DESC
            """;

    private final JdbcTemplate jdbc;

    public JdbcSessionRegistryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SessionSummary> listActive(UUID userId) {
        return jdbc.query(LIST_ACTIVE, (rs, n) -> new SessionSummary(
                rs.getObject("family_id", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_seen_at").toInstant(),
                rs.getString("ip_address"),
                rs.getString("user_agent")), userId);
    }
}
