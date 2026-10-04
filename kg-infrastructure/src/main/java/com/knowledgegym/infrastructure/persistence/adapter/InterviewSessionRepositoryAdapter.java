package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.learning.domain.model.InterviewAnswer;
import com.knowledgegym.learning.domain.model.InterviewSession;
import com.knowledgegym.learning.domain.port.InterviewSessionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.sql.Timestamp;

@Repository
public class InterviewSessionRepositoryAdapter implements InterviewSessionRepository {
    private final JdbcTemplate db;

    public InterviewSessionRepositoryAdapter(JdbcTemplate db) { this.db = db; }

    @Override
    @Transactional
    public InterviewSession save(InterviewSession session) {
        db.update("""
                INSERT INTO interview_sessions(id, user_id, topic_id, question_count, mode, status, started_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, session.id(), session.userId(), session.topicId(), session.questionCount(),
                session.mode(), session.status(), Timestamp.from(session.startedAt()));
        // Assignment placeholders persist membership; NULL score means not yet submitted.
        // `display_order` giữ đúng thứ tự đã giao cho user (V017) — `answer()` upsert không đụng cột này.
        int order = 0;
        for (UUID question : session.questionIds()) {
            db.update("INSERT INTO interview_answers(session_id, question_id, display_order) VALUES (?, ?, ?)",
                    session.id(), question, order++);
        }
        return session;
    }

    private List<InterviewSession> read(String sql, Object... args) {
        List<Row> rows = db.query(sql, (rs, row) -> {
            var end = rs.getTimestamp("finished_at");
            return new Row(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                    rs.getObject("topic_id", UUID.class), rs.getInt("question_count"),
                    rs.getString("mode"), rs.getString("status"),
                    rs.getTimestamp("started_at").toInstant(), end == null ? null : end.toInstant());
        }, args);
        Map<UUID, List<UUID>> membership = membershipOf(rows.stream().map(Row::id).toList());
        return rows.stream().map(row -> new InterviewSession(row.id(), row.userId(), row.topicId(),
                row.questionCount(), row.mode(), row.status(), row.startedAt(),
                row.finishedAt(), membership.getOrDefault(row.id(), List.of()))).toList();
    }

    /** Membership của cả trang trong 1 query — history tới 100 session nên đọc từng cái là N+1. */
    private Map<UUID, List<UUID>> membershipOf(List<UUID> sessionIds) {
        Map<UUID, List<UUID>> result = new LinkedHashMap<>();
        if (sessionIds.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", Collections.nCopies(sessionIds.size(), "?"));
        db.query("SELECT session_id, question_id FROM interview_answers WHERE session_id IN ("
                        + placeholders + ") ORDER BY session_id, display_order",
                rs -> {
                    result.computeIfAbsent(rs.getObject("session_id", UUID.class), key -> new ArrayList<>())
                            .add(rs.getObject("question_id", UUID.class));
                }, sessionIds.toArray());
        return result;
    }

    private record Row(UUID id, UUID userId, UUID topicId, int questionCount, String mode, String status,
                       Instant startedAt, Instant finishedAt) {
    }

    @Override
    public Optional<InterviewSession> findByIdAndUserIdForUpdate(UUID id, UUID user) {
        return read("SELECT * FROM interview_sessions WHERE id = ? AND user_id = ? FOR UPDATE", id, user)
                .stream().findFirst();
    }

    @Override
    public Optional<InterviewSession> findByIdAndUserId(UUID id, UUID user) {
        return read("SELECT * FROM interview_sessions WHERE id = ? AND user_id = ?", id, user)
                .stream().findFirst();
    }

    @Override
    public Map<UUID, InterviewAnswer> answersOf(UUID sessionId) {
        Map<UUID, InterviewAnswer> answers = new LinkedHashMap<>();
        db.query("""
                SELECT question_id, user_answer, answer_html, attempted_at
                FROM interview_answers
                WHERE session_id = ? AND user_answer IS NOT NULL AND btrim(user_answer) <> ''
                ORDER BY display_order
                """, rs -> {
            UUID questionId = rs.getObject("question_id", UUID.class);
            answers.put(questionId, new InterviewAnswer(sessionId, questionId, rs.getString("user_answer"),
                    rs.getString("answer_html"), rs.getTimestamp("attempted_at").toInstant()));
        }, sessionId);
        return answers;
    }

    @Override
    public List<InterviewSession> findByUserId(UUID user, int page, int size) {
        return read("SELECT * FROM interview_sessions WHERE user_id = ? ORDER BY started_at DESC, id LIMIT ? OFFSET ?",
                user, size, (long) (page - 1) * size);
    }

    @Override
    public long countByUserId(UUID user) {
        return db.queryForObject("SELECT count(*) FROM interview_sessions WHERE user_id = ?", Long.class, user);
    }

    @Override
    public void upsertAnswer(InterviewAnswer answer) {
        // `display_order` chỉ có ý nghĩa với row placeholder do `save()` tạo; ON CONFLICT không cập nhật
        // cột này nên thứ tự giao câu được giữ nguyên. Row không có placeholder rơi về 0.
        db.update("""
                INSERT INTO interview_answers(session_id, question_id, user_answer, answer_html,
                                              attempted_at, display_order)
                VALUES (?, ?, ?, ?, ?, 0)
                ON CONFLICT(session_id, question_id) DO UPDATE SET
                    user_answer = excluded.user_answer, answer_html = excluded.answer_html,
                    attempted_at = excluded.attempted_at
                """, answer.sessionId(), answer.questionId(), answer.userAnswer(),
                answer.answerHtml(), Timestamp.from(answer.attemptedAt()));
    }

    @Override
    public void finish(UUID sessionId, Instant now) {
        db.update("UPDATE interview_sessions SET status = 'FINISHED', finished_at = ? WHERE id = ?",
                Timestamp.from(now), sessionId);
    }
}
