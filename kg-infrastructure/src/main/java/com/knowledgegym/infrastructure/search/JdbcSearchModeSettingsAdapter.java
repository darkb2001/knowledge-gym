package com.knowledgegym.infrastructure.search;

import com.knowledgegym.search.domain.port.SearchModeSettingsPort;
import com.knowledgegym.search.domain.port.SearchModeSettingsPort.Mode;
import com.knowledgegym.search.domain.port.SearchModeSettingsPort.Settings;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcSearchModeSettingsAdapter implements SearchModeSettingsPort {
    private final JdbcTemplate jdbc;

    public JdbcSearchModeSettingsAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Settings current() {
        return jdbc.queryForObject(
                "SELECT mode, version, updated_at, updated_by FROM search_runtime_settings WHERE id=1",
                (rs, row) -> map(rs));
    }

    @Override
    @Transactional
    public Settings update(Mode mode, long expectedVersion, UUID actorId) {
        int changed = jdbc.update(
                "UPDATE search_runtime_settings SET mode=?, version=version+1, updated_at=NOW(), updated_by=? "
                        + "WHERE id=1 AND version=?",
                mode.name(), actorId, expectedVersion);
        if (changed != 1) throw new IllegalStateException("optimistic version conflict");
        Settings updated = current();
        jdbc.update("INSERT INTO search_runtime_setting_audit(mode, version, changed_by) VALUES (?, ?, ?)",
                updated.mode().name(), updated.version(), actorId);
        return updated;
    }

    private static Settings map(ResultSet rs) throws java.sql.SQLException {
        return new Settings(Mode.valueOf(rs.getString("mode")), rs.getLong("version"),
                rs.getTimestamp("updated_at").toInstant(), rs.getObject("updated_by", UUID.class));
    }
}
