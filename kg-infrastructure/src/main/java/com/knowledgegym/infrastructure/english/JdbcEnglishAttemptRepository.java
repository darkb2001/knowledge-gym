package com.knowledgegym.infrastructure.english;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.knowledgegym.english.domain.model.EnglishAttempt;
import com.knowledgegym.english.domain.port.EnglishAttemptRepository;
import com.knowledgegym.shared.application.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class JdbcEnglishAttemptRepository implements EnglishAttemptRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public JdbcEnglishAttemptRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    private RowMapper<EnglishAttempt> mapper() { return (rs, row) -> {
        try {
            return new EnglishAttempt(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("exercise_id"), rs.getString("status"), rs.getLong("version"),
                json.readValue(rs.getString("answers"), new TypeReference<Map<String, Integer>>() {}),
                rs.getString("response"), rs.getInt("elapsed_seconds"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
        } catch (tools.jackson.core.JacksonException e) { throw new IllegalStateException("Invalid stored English answers", e); }
    }; }
    @Override @Transactional
    public EnglishAttempt startOrResume(UUID user, String exerciseId) {
        // Serialise quota checks per owner; no cross-user locks or client-supplied actors.
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", UUID.class, user);
        var existing = jdbc.query("SELECT * FROM english_attempts WHERE user_id=? AND exercise_id=? AND status='DRAFT'",
            mapper(), user, exerciseId);
        if (!existing.isEmpty()) return existing.getFirst();
        Long count = jdbc.queryForObject("SELECT count(*) FROM english_attempts WHERE user_id=?", Long.class, user);
        if (count != null && count >= 1000) throw new ConflictException("English practice history limit reached");
        return jdbc.queryForObject("INSERT INTO english_attempts(id,user_id,exercise_id) VALUES(?,?,?) RETURNING *",
            mapper(), UUID.randomUUID(), user, exerciseId);
    }
    @Override public Optional<EnglishAttempt> findOwned(UUID user, UUID id) {
        return jdbc.query("SELECT * FROM english_attempts WHERE id=? AND user_id=?", mapper(), id, user).stream().findFirst();
    }
    @Override public Page listOwned(UUID user, int page, int size) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM english_attempts WHERE user_id=?", Long.class, user);
        var items = jdbc.query("SELECT * FROM english_attempts WHERE user_id=? ORDER BY updated_at DESC,id LIMIT ? OFFSET ?",
            mapper(), user, size, (page - 1) * size);
        return new Page(items, count == null ? 0 : count, page, size);
    }
    @Override public boolean updateOwned(EnglishAttempt a, long expectedVersion) {
        try {
            return jdbc.update("UPDATE english_attempts SET answers=?::jsonb,response=?,elapsed_seconds=?,status=?,"
                    + "version=version+1,updated_at=? WHERE id=? AND user_id=? AND status='DRAFT' AND version=?",
                json.writeValueAsString(a.answers()), a.response(), a.elapsedSeconds(), a.status(),
                java.sql.Timestamp.from(a.updatedAt()), a.id(), a.userId(), expectedVersion) == 1;
        } catch (tools.jackson.core.JacksonException e) { throw new IllegalStateException(e); }
    }
}
