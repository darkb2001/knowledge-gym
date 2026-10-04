package com.knowledgegym.infrastructure.security;

import com.knowledgegym.identity.domain.port.SessionInvalidationPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Cắt access token bằng cột {@code users.tokens_invalid_before} (đọc ở {@link JdbcAccessTokenGuard}).
 * Chỉ update 1 dòng users, KHÔNG revoke refresh token — thiết bị khác vẫn refresh bình thường
 * nên không bị đăng xuất dây chuyền.
 */
@Component
public class JdbcSessionInvalidationAdapter implements SessionInvalidationPort {

    private final JdbcTemplate jdbc;

    public JdbcSessionInvalidationAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void invalidateIssuedBefore(UUID userId, Instant cutoff) {
        if (userId == null || cutoff == null) {
            return;
        }
        // clock_timestamp() (không phải now()) để mốc cắt luôn >= mọi iat đã phát hành trong transaction này.
        jdbc.update("UPDATE users SET tokens_invalid_before=GREATEST(clock_timestamp(), ?::timestamptz), updated_at=now() WHERE id=?",
                Timestamp.from(cutoff), userId);
    }
}
