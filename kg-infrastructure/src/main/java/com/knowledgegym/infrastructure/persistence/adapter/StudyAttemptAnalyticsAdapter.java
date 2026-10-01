package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.progress.domain.model.HeatmapDay;
import com.knowledgegym.progress.domain.port.StudyAttemptAnalytics;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class StudyAttemptAnalyticsAdapter implements StudyAttemptAnalytics {
    private final JdbcTemplate jdbc;
    public StudyAttemptAnalyticsAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<LocalDate> activeDates(UUID userId, ZoneId zone) {
        return jdbc.query("""
                SELECT DISTINCT (attempted_at AT TIME ZONE ?)::date
                FROM study_attempts WHERE user_id = ? ORDER BY 1
                """, (rs, row) -> rs.getDate(1).toLocalDate(), zone.getId(), userId);
    }

    @Override
    public Map<UUID, List<LocalDate>> activeDatesByModule(UUID userId, ZoneId zone) {
        Map<UUID, List<LocalDate>> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT q.module_id, (a.attempted_at AT TIME ZONE ?)::date AS active_date
                FROM study_attempts a JOIN questions q ON q.id = a.question_id
                WHERE a.user_id = ? GROUP BY q.module_id, active_date ORDER BY q.module_id, active_date
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        result.computeIfAbsent(rs.getObject("module_id", UUID.class), key -> new ArrayList<>())
                                .add(rs.getDate("active_date").toLocalDate()), zone.getId(), userId);
        return result;
    }

    @Override
    public List<HeatmapDay> heatmap(UUID userId, LocalDate from, LocalDate through, ZoneId zone) {
        Timestamp start = Timestamp.from(from.atStartOfDay(zone).toInstant());
        Timestamp end = Timestamp.from(through.plusDays(1).atStartOfDay(zone).toInstant());
        return jdbc.query("""
                WITH days AS (
                    SELECT generate_series(?::date, ?::date, INTERVAL '1 day')::date AS day
                ), counts AS (
                    SELECT (attempted_at AT TIME ZONE ?)::date AS day, count(*) AS amount
                    FROM study_attempts
                    WHERE user_id = ? AND attempted_at >= ? AND attempted_at < ?
                    GROUP BY 1
                )
                SELECT days.day, COALESCE(counts.amount, 0) AS amount
                FROM days LEFT JOIN counts USING (day) ORDER BY days.day
                """, (rs, row) -> new HeatmapDay(rs.getDate("day").toLocalDate(), rs.getLong("amount")),
                Date.valueOf(from), Date.valueOf(through), zone.getId(), userId, start, end);
    }
}
