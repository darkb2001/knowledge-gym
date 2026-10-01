package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.progress.domain.model.UserXpRow;
import com.knowledgegym.progress.domain.port.UserXpRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class UserXpAdapter implements UserXpRepository {
    private final JdbcTemplate jdbc;
    public UserXpAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void lockQuestion(UUID userId, UUID questionId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(? || ':' || ?, 0))",
                rs -> { while (rs.next()) { /* consume void result */ } }, userId.toString(), questionId.toString());
    }

    @Override
    public Map<UUID, FirstAttempt> firstAttempts(UUID userId, Collection<UUID> questionIds) {
        if (questionIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(questionIds.size(), "?"));
        Object[] args = new Object[questionIds.size() + 1];
        args[0] = userId;
        int index = 1;
        for (UUID id : questionIds) args[index++] = id;
        Map<UUID, FirstAttempt> result = new LinkedHashMap<>();
        jdbc.query("SELECT DISTINCT ON (question_id) question_id, id, attempted_at, source, is_correct " +
                        "FROM study_attempts WHERE user_id = ? AND question_id IN (" + placeholders + ") " +
                        "ORDER BY question_id, attempted_at, id",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    UUID questionId = rs.getObject("question_id", UUID.class);
                    result.put(questionId, new FirstAttempt(rs.getObject("id", UUID.class),
                            rs.getTimestamp("attempted_at").toInstant(),
                            com.knowledgegym.learning.domain.model.AttemptSource.valueOf(rs.getString("source")),
                            rs.getBoolean("is_correct")));
                }, args);
        return result;
    }

    @Override
    public Map<UUID, String> displayNamesByIds(Collection<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(userIds.size(), "?"));
        Map<UUID, String> result = new LinkedHashMap<>();
        jdbc.query("SELECT id, display_name FROM users WHERE id IN (" + placeholders + ")",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        result.put(rs.getObject("id", UUID.class), rs.getString("display_name")), userIds.toArray());
        return result;
    }

    @Override
    public void addXp(UUID userId, int delta) {
        if (delta != 0) jdbc.update("UPDATE users SET xp = xp + ? WHERE id = ?", delta, userId);
    }

    @Override
    public int currentXp(UUID userId) {
        Integer value = jdbc.queryForObject("SELECT xp FROM users WHERE id = ?", Integer.class, userId);
        return value == null ? 0 : value;
    }

    @Override
    public int xpOf(UUID userId) {
        Integer value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(xp), 0)::integer FROM (
                    SELECT DISTINCT ON (question_id)
                        CASE WHEN is_correct THEN CASE source WHEN 'FLASHCARD' THEN 10
                             WHEN 'DAILY' THEN 15 ELSE 5 END ELSE 1 END AS xp
                    FROM study_attempts WHERE user_id = ?
                    ORDER BY question_id, attempted_at, id
                ) attempts
                """, Integer.class, userId);
        return value == null ? 0 : value;
    }

    @Override
    public List<UserXpRow> topByXp(int limit) {
        return jdbc.query("SELECT id, xp FROM users WHERE xp > 0 ORDER BY xp DESC, id DESC LIMIT ?",
                (rs, row) -> new UserXpRow(rs.getObject("id", UUID.class), rs.getInt("xp")), limit);
    }
}
