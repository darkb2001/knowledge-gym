package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.learning.domain.model.QuizAnswer;
import com.knowledgegym.learning.domain.model.QuizSession;
import com.knowledgegym.learning.domain.model.QuizStrategy;
import com.knowledgegym.learning.domain.port.QuizSessionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Native writes share the datasource and transaction of the JPA transaction manager. */
@Repository
public class QuizSessionRepositoryAdapter implements QuizSessionRepository {
    private final JdbcTemplate db;

    public QuizSessionRepositoryAdapter(JdbcTemplate db) { this.db = db; }

    @Override
    @Transactional
    public QuizSession save(QuizSession session) {
        int created = db.update("""
                INSERT INTO quiz_sessions(id, user_id, strategy, total, started_at)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT(id) DO NOTHING
                """, session.getId(), session.getUserId(), session.getStrategy().name(),
                session.getTotal(), Timestamp.from(session.getStartedAt()));
        if (created == 1) {
            int order = 0;
            for (UUID question : session.getQuestionIds()) {
                db.update("INSERT INTO quiz_session_questions(session_id, question_id, display_order) VALUES (?, ?, ?)",
                        session.getId(), question, order++);
            }
        } else {
            db.update("UPDATE quiz_sessions SET score = ?, finished_at = ? WHERE id = ? AND user_id = ?",
                    session.getScore(), session.getFinishedAt() == null ? null : Timestamp.from(session.getFinishedAt()),
                    session.getId(), session.getUserId());
        }
        return session;
    }

    private List<QuizSession> read(String sql, Object... args) {
        List<Row> rows = db.query(sql, (rs, row) -> {
            var end = rs.getTimestamp("finished_at");
            return new Row(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                    QuizStrategy.valueOf(rs.getString("strategy")), (Integer) rs.getObject("score"),
                    (Integer) rs.getObject("total"), rs.getTimestamp("started_at").toInstant(),
                    end == null ? null : end.toInstant());
        }, args);
        Map<UUID, List<UUID>> membership = membershipOf(rows.stream().map(Row::id).toList());
        return rows.stream().map(row -> QuizSession.rehydrate(row.id(), row.userId(), row.strategy(),
                row.score(), row.total(), row.startedAt(), row.finishedAt(),
                membership.getOrDefault(row.id(), List.of()))).toList();
    }

    /**
     * Membership của **cả** trang trong 1 query. Đọc từng session một là N+1, mà `/quiz/history`
     * có tới 100 session/trang.
     */
    private Map<UUID, List<UUID>> membershipOf(List<UUID> sessionIds) {
        Map<UUID, List<UUID>> result = new LinkedHashMap<>();
        if (sessionIds.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", Collections.nCopies(sessionIds.size(), "?"));
        db.query("SELECT session_id, question_id FROM quiz_session_questions WHERE session_id IN ("
                        + placeholders + ") ORDER BY session_id, display_order",
                rs -> {
                    result.computeIfAbsent(rs.getObject("session_id", UUID.class), key -> new ArrayList<>())
                            .add(rs.getObject("question_id", UUID.class));
                }, sessionIds.toArray());
        return result;
    }

    private record Row(UUID id, UUID userId, QuizStrategy strategy, Integer score, Integer total,
                       java.time.Instant startedAt, java.time.Instant finishedAt) {
    }

    @Override
    public Optional<QuizSession> findByIdAndUserId(UUID id, UUID user) {
        return read("SELECT * FROM quiz_sessions WHERE id = ? AND user_id = ?", id, user).stream().findFirst();
    }

    @Override
    public Optional<QuizSession> findByIdAndUserIdForUpdate(UUID id, UUID user) {
        return read("SELECT * FROM quiz_sessions WHERE id = ? AND user_id = ? FOR UPDATE", id, user).stream().findFirst();
    }

    @Override
    public List<QuizSession> findByUserId(UUID user, int page, int size) {
        return read("SELECT * FROM quiz_sessions WHERE user_id = ? ORDER BY started_at DESC, id LIMIT ? OFFSET ?",
                user, size, (long) (page - 1) * size);
    }

    @Override
    public long countByUserId(UUID user) {
        return db.queryForObject("SELECT count(*) FROM quiz_sessions WHERE user_id = ?", Long.class, user);
    }

    @Override
    public int insertAnswersIgnoringDuplicates(List<QuizAnswer> answers) {
        int inserted = 0;
        for (var answer : answers) {
            inserted += db.update("""
                    INSERT INTO quiz_answers(session_id, question_id, selected_option_id, answer_text, is_correct, time_ms)
                    VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(session_id, question_id) DO NOTHING
                    """, answer.sessionId(), answer.questionId(), answer.selectedOptionId(),
                    answer.answerText(), answer.correct(), answer.timeMs());
        }
        return inserted;
    }
}
