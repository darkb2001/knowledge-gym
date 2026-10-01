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
import java.math.BigDecimal;
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
                    rs.getString("mode"), rs.getString("status"), rs.getBigDecimal("overall_score"),
                    rs.getTimestamp("started_at").toInstant(), end == null ? null : end.toInstant());
        }, args);
        Map<UUID, List<UUID>> membership = membershipOf(rows.stream().map(Row::id).toList());
        return rows.stream().map(row -> new InterviewSession(row.id(), row.userId(), row.topicId(),
                row.questionCount(), row.mode(), row.status(), row.overallScore(), row.startedAt(),
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
                       BigDecimal overallScore, Instant startedAt, Instant finishedAt) {
    }

    @Override
    public Optional<InterviewSession> findByIdAndUserIdForUpdate(UUID id, UUID user) {
        return read("SELECT * FROM interview_sessions WHERE id = ? AND user_id = ? FOR UPDATE", id, user)
                .stream().findFirst();
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
                INSERT INTO interview_answers(session_id, question_id, user_answer, keyword_score, feedback,
                                              sample_answer, attempted_at, display_order)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                ON CONFLICT(session_id, question_id) DO UPDATE SET
                    user_answer = excluded.user_answer, keyword_score = excluded.keyword_score,
                    feedback = excluded.feedback, sample_answer = excluded.sample_answer,
                    attempted_at = excluded.attempted_at
                """, answer.sessionId(), answer.questionId(), answer.userAnswer(), answer.keywordScore(),
                answer.feedback(), answer.sampleAnswer(), Timestamp.from(answer.attemptedAt()));
    }

    @Override
    public List<InterviewAnswer> findSubmittedAnswers(UUID sessionId) {
        return db.query("SELECT * FROM interview_answers WHERE session_id = ? AND keyword_score IS NOT NULL",
                (rs, row) -> new InterviewAnswer(sessionId, rs.getObject("question_id", UUID.class),
                        rs.getString("user_answer"), rs.getBigDecimal("keyword_score"), rs.getString("feedback"),
                        rs.getString("sample_answer"), rs.getTimestamp("attempted_at").toInstant()), sessionId);
    }

    @Override
    public void finish(UUID sessionId, BigDecimal score, Instant now) {
        db.update("UPDATE interview_sessions SET status = 'FINISHED', overall_score = ?, finished_at = ? WHERE id = ?",
                score, Timestamp.from(now), sessionId);
    }
}
