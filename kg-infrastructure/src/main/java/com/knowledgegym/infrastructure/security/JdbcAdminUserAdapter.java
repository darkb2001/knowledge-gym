package com.knowledgegym.infrastructure.security;

import com.knowledgegym.identity.domain.port.AdminUserPort;
import com.knowledgegym.shared.application.NotFoundException;
import com.knowledgegym.shared.domain.model.UserRole;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class JdbcAdminUserAdapter implements AdminUserPort {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcAdminUserAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String COLUMNS = "id,email,display_name,role,blocked,email_verified,auth_provider,xp,created_at,updated_at";
    private static final RowMapper<UserView> MAPPER = (rs, n) -> new UserView(
            rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name"),
            UserRole.valueOf(rs.getString("role")), rs.getBoolean("blocked"),
            rs.getBoolean("email_verified"), rs.getString("auth_provider"), rs.getInt("xp"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());

    @Override public UserPage list(String query, UserRole role, Boolean blocked, int page, int size) {
        var p = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (query != null && !query.isBlank()) {
            // Literal substring search: '%' and '_' do not turn into wildcards.
            where.append(" AND (strpos(lower(email),:q)>0 OR strpos(lower(display_name),:q)>0)");
            p.addValue("q", query.trim().toLowerCase(java.util.Locale.ROOT));
        }
        if (role != null) { where.append(" AND role=:role"); p.addValue("role", role.name()); }
        if (blocked != null) { where.append(" AND blocked=:blocked"); p.addValue("blocked", blocked); }
        long total = jdbc.queryForObject("SELECT count(*) FROM users" + where, p, Long.class);
        p.addValue("limit", size).addValue("offset", (long) (page - 1) * size);
        var items = jdbc.query("SELECT " + COLUMNS + " FROM users" + where
                + " ORDER BY created_at DESC,id DESC LIMIT :limit OFFSET :offset", p, MAPPER);
        return new UserPage(items, page, size, total, (int) ((total + size - 1) / size));
    }
    @Override public UserView get(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM users WHERE id=:id",
                new MapSqlParameterSource("id", id), MAPPER).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("User không tồn tại: " + id));
    }
    @Override public void lockAdministration() {
        // Transaction-scoped global mutex; prevents two admins removing each other concurrently.
        jdbc.getJdbcTemplate().execute("SELECT pg_advisory_xact_lock(75402127)");
    }
    @Override public long activeAdminCount() {
        return jdbc.getJdbcTemplate().queryForObject(
                "SELECT count(*) FROM users WHERE role='ADMIN' AND NOT blocked", Long.class);
    }
    @Override public void changeRole(UUID id, UserRole role) {
        jdbc.update("UPDATE users SET role=:role,updated_at=now() WHERE id=:id",
                new MapSqlParameterSource("id", id).addValue("role", role.name()));
    }
    @Override public void setBlocked(UUID id, boolean blocked) {
        jdbc.update("UPDATE users SET blocked=:blocked,updated_at=now() WHERE id=:id",
                new MapSqlParameterSource("id", id).addValue("blocked", blocked));
    }
    @Override public void revokeSessions(UUID id) {
        var p = new MapSqlParameterSource("id", id);
        // Cùng lý do như JdbcSessionInvalidationAdapter: iat chỉ có giây nên mốc cắt làm tròn xuống giây.
        jdbc.update("UPDATE users SET tokens_invalid_before=date_trunc('second', clock_timestamp()),updated_at=now() WHERE id=:id", p);
        jdbc.update("UPDATE refresh_tokens SET revoked_at=now() WHERE user_id=:id AND revoked_at IS NULL", p);
    }
    @Override public void audit(UUID actor, UUID target, String action, String reason) {
        jdbc.update("""
                INSERT INTO audit_logs(user_id,action,entity_type,entity_id,details)
                VALUES (:actor,:action,'USER',:target,jsonb_build_object('reason',CAST(:reason AS text)))
                """, new MapSqlParameterSource("actor", actor).addValue("target", target)
                .addValue("action", action).addValue("reason", reason));
    }
}
