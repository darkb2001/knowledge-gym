package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.learning.domain.port.AdminLearningPort;
import com.knowledgegym.shared.application.NotFoundException;
import com.knowledgegym.shared.domain.model.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.util.UUID;

@Component
public class JdbcAdminLearningAdapter implements AdminLearningPort {
    private final JdbcTemplate jdbc;
    public JdbcAdminLearningAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public PageResult<Entry> list(UUID userId, Kind kind, int page, int size) {
        if (jdbc.queryForObject("SELECT count(*) FROM users WHERE id=?",Integer.class,userId)==0)
            throw new NotFoundException("User không tồn tại");
        // Identifiers and SQL are selected exclusively from a server enum, never client text.
        String table = switch(kind) { case QUIZ->"quiz_sessions"; case SRS->"srs_cards";
            case INTERVIEW->"interview_sessions"; case PROGRESS->"user_progress"; };
        String query = switch(kind) {
            case QUIZ -> "SELECT id,strategy AS label,CASE WHEN finished_at IS NULL THEN 'ACTIVE' ELSE 'FINISHED' END AS status,score::numeric AS score,total,NULL::date AS due,started_at AS occurred FROM quiz_sessions WHERE user_id=?";
            case SRS -> "SELECT c.id,q.title AS label,'ENROLLED' AS status,NULL::numeric AS score,c.repetitions AS total,c.next_review AS due,c.last_reviewed_at AS occurred FROM srs_cards c JOIN questions q ON q.id=c.question_id WHERE c.user_id=?";
            case INTERVIEW -> "SELECT s.id,t.name AS label,s.status,NULL::numeric AS score,s.question_count AS total,NULL::date AS due,s.started_at AS occurred FROM interview_sessions s JOIN topics t ON t.id=s.topic_id WHERE s.user_id=?";
            case PROGRESS -> "SELECT p.id,m.name AS label,'RECORDED' AS status,p.mastery_pct AS score,p.total_attempts AS total,NULL::date AS due,p.last_active_at AS occurred FROM user_progress p JOIN modules m ON m.id=p.module_id WHERE p.user_id=?";
        };
        long total = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id=?",Long.class,userId);
        var rows = jdbc.query("SELECT * FROM (" + query + ") records ORDER BY occurred DESC NULLS LAST,id DESC LIMIT ? OFFSET ?",(rs,n)-> {
            var due = rs.getDate("due"); Timestamp occurred = rs.getTimestamp("occurred");
            return new Entry(rs.getObject("id",UUID.class),rs.getString("label"),rs.getString("status"),
                    rs.getBigDecimal("score"),(Integer)rs.getObject("total"),due==null?null:due.toLocalDate(),occurred==null?null:occurred.toInstant());
        },userId,size,(long)(page-1)*size);
        return new PageResult<>(rows,page,size,total);
    }
    @Override public void resetCard(UUID actor, UUID userId, UUID cardId, String reason) {
        int updated = jdbc.update("UPDATE srs_cards SET interval_days=0,ease_factor=2.50,repetitions=0,next_review=CURRENT_DATE,last_reviewed_at=NULL WHERE id=? AND user_id=?",cardId,userId);
        if (updated==0) throw new NotFoundException("Thẻ không thuộc tài khoản này hoặc không tồn tại");
        jdbc.update("""
                INSERT INTO audit_logs(user_id,action,entity_type,entity_id,details)
                VALUES (?,'ADMIN_SRS_RESET','SRS_CARD',?,jsonb_build_object('reason',CAST(? AS text),'targetUserId',CAST(? AS text)))
                """,actor,cardId,reason,userId.toString());
        // Only scheduling is reset. Preserve study_attempts, quiz/interview history, XP and mastery.
    }
}
