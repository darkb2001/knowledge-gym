package com.knowledgegym.infrastructure.blog;

import com.knowledgegym.blog.domain.port.BlogScheduleSettingsPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcBlogScheduleSettingsAdapter implements BlogScheduleSettingsPort {
    private final JdbcTemplate jdbc;
    private final com.knowledgegym.blog.domain.port.BlogPostRepository posts;

    public JdbcBlogScheduleSettingsAdapter(JdbcTemplate jdbc,
                                           com.knowledgegym.blog.domain.port.BlogPostRepository posts) {
        this.jdbc = jdbc;
        this.posts = posts;
    }

    @Override
    public Settings current() {
        return jdbc.queryForObject(
                "SELECT schedule_enabled,local_time,timezone,daily_limit,publish_policy,quality_threshold,last_scheduled_date,last_run_at FROM blog_writer_settings WHERE id=TRUE",
                (rs, n) -> new Settings(rs.getBoolean(1), rs.getTime(2).toLocalTime(), ZoneId.of(rs.getString(3)),
                        rs.getInt(4), PublishPolicy.valueOf(rs.getString(5)), rs.getInt(6),
                        rs.getObject(7, LocalDate.class),
                        rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant()));
    }

    @Override
    @Transactional
    public Settings update(boolean enabled, LocalTime time, ZoneId zone, int dailyLimit,
                           PublishPolicy policy, int threshold, UUID actor) {
        String previous = jdbc.queryForObject("SELECT publish_policy FROM blog_writer_settings WHERE id=TRUE", String.class);
        jdbc.update("UPDATE blog_writer_settings SET schedule_enabled=?,local_time=?,timezone=?,daily_limit=?,publish_policy=?,quality_threshold=?,updated_by=?,updated_at=NOW() WHERE id=TRUE",
                enabled, Time.valueOf(time), zone.getId(), dailyLimit, policy.name(), threshold, actor);
        String details = "{\"scheduleEnabled\":" + enabled + ",\"dailyLimit\":" + dailyLimit
                + ",\"qualityThreshold\":" + threshold + ",\"timezone\":\"" + zone.getId() + "\"}";
        jdbc.update("INSERT INTO blog_writer_audit(actor_id,action,old_policy,new_policy,details) VALUES(?,?,?,?,?::jsonb)",
                actor, "SETTINGS_UPDATED", previous, policy.name(), details);
        return current();
    }

    @Override
    @Transactional
    public boolean enqueueScheduled(LocalDate date, Instant scheduledAt, int dailyLimit) {
        int locked = jdbc.update(
                "UPDATE blog_writer_settings SET last_scheduled_date=?,updated_at=NOW() WHERE id=TRUE AND schedule_enabled=TRUE AND (last_scheduled_date IS NULL OR last_scheduled_date<?)",
                date, date);
        if (locked == 0) return false;
        for (int i = 1; i <= dailyLimit; i++) {
            jdbc.update("INSERT INTO blog_generation_queue(topic,angle,priority,selection_strategy,writer_strategy,reference_items,status,scheduled_for,idempotency_key) VALUES(?,?,0,'TRENDING','AUTO','[]'::jsonb,'QUEUED',?,?) ON CONFLICT DO NOTHING",
                    "Daily Knowledge Gym article", "Select a high-value recent Java/Spring knowledge source",
                    Timestamp.from(scheduledAt), "schedule:" + date + ":" + i);
        }
        return true;
    }

    @Override
    @Transactional
    public UUID enqueueNow(String topic, UUID actor, String requestId) {
        String actual = topic == null || topic.isBlank() ? "Daily Knowledge Gym article" : topic.trim();
        String idem = "manual:" + requestId;
        List<UUID> inserted = jdbc.query(
                "INSERT INTO blog_generation_queue(topic,angle,priority,selection_strategy,writer_strategy,reference_items,status,scheduled_for,idempotency_key,requested_by) VALUES(?,?,100,'TRENDING','AUTO','[]'::jsonb,'QUEUED',NOW(),?,?) ON CONFLICT(idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING RETURNING id",
                (rs, n) -> rs.getObject(1, UUID.class), actual,
                "Focus on the latest relevant collected references", idem, actor);
        if (!inserted.isEmpty()) return inserted.getFirst();
        return jdbc.queryForObject("SELECT id FROM blog_generation_queue WHERE idempotency_key=?", UUID.class, idem);
    }

    @Override
    public Job findJob(UUID id) {
        return jdbc.query(
                "SELECT id,topic,angle,idempotency_key,requested_by,status,attempts,error_message,generated_post_id FROM blog_generation_queue WHERE id=?",
                this::mapJob, id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Writer run not found"));
    }

    @Override
    @Transactional
    public Job claimNext() {
        List<Job> jobs = jdbc.query(
                "UPDATE blog_generation_queue SET status='GENERATING',attempts=attempts+1,started_at=NOW(),error_message=NULL WHERE id=("
                        + "SELECT id FROM blog_generation_queue WHERE status='QUEUED' AND (retry_after IS NULL OR retry_after<=NOW()) "
                        + "AND (scheduled_for IS NULL OR scheduled_for<=NOW()) ORDER BY priority DESC,scheduled_for NULLS FIRST "
                        + "LIMIT 1 FOR UPDATE SKIP LOCKED) "
                        + "RETURNING id,topic,angle,idempotency_key,requested_by,status,attempts,error_message,generated_post_id",
                this::mapJob);
        return jobs.stream().findFirst().orElse(null);
    }

    @Override
    @Transactional
    public int reclaimStaleGenerating(Duration staleAfter) {
        long seconds = Math.max(60, staleAfter == null ? 900 : staleAfter.toSeconds());
        // Draft already saved — mark DONE so we never regenerate duplicate posts.
        int completed = jdbc.update(
                "UPDATE blog_generation_queue SET status='DONE',completed_at=NOW(),retry_after=NULL,error_message='Recovered after restart' "
                        + "WHERE status='GENERATING' AND generated_post_id IS NOT NULL "
                        + "AND started_at < NOW() - make_interval(secs => ?)", (int) seconds);
        // Still generating with no draft — requeue for another attempt.
        int requeued = jdbc.update(
                "UPDATE blog_generation_queue SET status='QUEUED',retry_after=NOW(),error_message='Requeued after stale GENERATING' "
                        + "WHERE status='GENERATING' AND generated_post_id IS NULL "
                        + "AND started_at < NOW() - make_interval(secs => ?)", (int) seconds);
        return completed + requeued;
    }

    @Override
    @Transactional
    public void complete(UUID queueId, UUID runId, UUID postId, int tokens, double cost, String output) {
        settleGlobalBudget(runId, tokens, cost);
        jdbc.update("UPDATE blog_generation_queue SET status='DONE',completed_at=NOW(),retry_after=NULL,generated_post_id=COALESCE(generated_post_id,?) WHERE id=?",
                postId, queueId);
        jdbc.update("UPDATE agent_runs SET status='SUCCESS',finished_at=NOW(),output_summary=?,tokens_used=?,cost_usd=? WHERE id=?",
                output + "; postId=" + postId, tokens, cost, runId);
        jdbc.update("UPDATE blog_writer_settings SET last_run_at=NOW() WHERE id=TRUE");
    }

    @Override
    @Transactional
    public void fail(UUID queueId, UUID runId, String error) {
        int attempts = jdbc.queryForObject("SELECT attempts FROM blog_generation_queue WHERE id=?", Integer.class, queueId);
        String safe = error == null ? "Writer failed" : error.substring(0, Math.min(1000, error.length()));
        // Exponential backoff: 1m, 2m, 4m… capped at 30m.
        int backoffMinutes = Math.min(30, 1 << Math.max(0, attempts - 1));
        if (attempts < 3) {
            jdbc.update("UPDATE blog_generation_queue SET status='QUEUED',retry_after=NOW()+(? * INTERVAL '1 minute'),error_message=? WHERE id=?",
                    backoffMinutes, safe, queueId);
        } else {
            jdbc.update("UPDATE blog_generation_queue SET status='FAILED',completed_at=NOW(),error_message=? WHERE id=?",
                    safe, queueId);
        }
        jdbc.update("UPDATE agent_runs SET status='FAILED',finished_at=NOW(),error_message=? WHERE id=? AND status='RUNNING'",
                safe, runId);
        jdbc.update("UPDATE blog_writer_settings SET last_run_at=NOW() WHERE id=TRUE");
    }

    @Override
    @Transactional
    public void deferForBudget(UUID queueId, UUID runId) {
        jdbc.update("UPDATE blog_generation_queue SET status='QUEUED',attempts=GREATEST(0,attempts-1),retry_after=date_trunc('day',NOW())+INTERVAL '1 day',error_message='Deferred: daily AI budget exhausted' WHERE id=?",
                queueId);
        jdbc.update("UPDATE agent_runs SET status='FAILED',finished_at=NOW(),error_message='Deferred: daily AI budget exhausted' WHERE id=?",
                runId);
        releaseUnsettledBudget(runId);
    }

    @Override
    public boolean withinGlobalLimits(int requestLimit, int tokenLimit, double costLimit) {
        String zone = jdbc.queryForObject("SELECT timezone FROM blog_writer_settings WHERE id=TRUE", String.class);
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT count(*)<? AND COALESCE(sum(tokens_used),0)<? AND COALESCE(sum(cost_usd),0)<? "
                        + "FROM blog_ai_budget_reservations WHERE usage_date=(NOW() AT TIME ZONE ?)::date",
                Boolean.class, requestLimit, tokenLimit, costLimit, zone));
    }

    @Override
    @Transactional
    public boolean reserveGlobalBudget(UUID requestId, int requestLimit, int tokenLimit, double costLimit,
                                       int reservedTokens, double reservedCost) {
        jdbc.execute("SELECT pg_advisory_xact_lock(8201022)");
        String zone = jdbc.queryForObject("SELECT timezone FROM blog_writer_settings WHERE id=TRUE", String.class);
        boolean capacity = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT count(*)<? AND COALESCE(sum(tokens_used),0)+?<=? AND COALESCE(sum(cost_usd),0)+?<=? "
                        + "FROM blog_ai_budget_reservations WHERE usage_date=(NOW() AT TIME ZONE ?)::date",
                Boolean.class, requestLimit, reservedTokens, tokenLimit,
                java.math.BigDecimal.valueOf(reservedCost), java.math.BigDecimal.valueOf(costLimit), zone));
        if (!capacity) return false;
        return jdbc.update(
                "INSERT INTO blog_ai_budget_reservations(request_id,usage_date,tokens_used,cost_usd) "
                        + "VALUES(?,(NOW() AT TIME ZONE ?)::date,?,?) ON CONFLICT(request_id) DO NOTHING",
                requestId, zone, reservedTokens, reservedCost) == 1;
    }

    @Override
    @Transactional
    public void settleGlobalBudget(UUID requestId, int tokens, double cost) {
        jdbc.update("UPDATE blog_ai_budget_reservations SET tokens_used=?,cost_usd=?,finalized=TRUE WHERE request_id=?",
                Math.max(0, tokens), java.math.BigDecimal.valueOf(Math.max(0, cost)), requestId);
        jdbc.update("UPDATE agent_runs SET tokens_used=?,cost_usd=? WHERE id=?",
                Math.max(0, tokens), java.math.BigDecimal.valueOf(Math.max(0, cost)), requestId);
    }

    @Override
    @Transactional
    public void releaseUnsettledBudget(UUID requestId) {
        jdbc.update("UPDATE blog_ai_budget_reservations SET tokens_used=0,cost_usd=0,finalized=TRUE "
                        + "WHERE request_id=? AND finalized=FALSE",
                requestId);
    }

    @Override
    @Transactional
    public boolean publishIfStillQualified(UUID postId, int score) {
        var config = jdbc.queryForObject(
                "SELECT publish_policy,quality_threshold FROM blog_writer_settings WHERE id=TRUE FOR UPDATE",
                (rs, n) -> new Object[]{rs.getString(1), rs.getInt(2)});
        if (!PublishPolicy.AUTO_PUBLISH_QUALIFIED.name().equals(config[0]) || score < (Integer) config[1]) {
            return false;
        }
        posts.publish(postId);
        return true;
    }

    private Job mapJob(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Job(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getObject(5, UUID.class), rs.getString(6), rs.getInt(7), rs.getString(8),
                rs.getObject(9, UUID.class));
    }
}
