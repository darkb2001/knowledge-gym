package com.knowledgegym.progress.application;

import com.knowledgegym.progress.domain.model.HeatmapDay;
import com.knowledgegym.progress.domain.port.StudyAttemptAnalytics;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public class QueryHeatmapUseCase {
    public static final int DEFAULT_DAYS = 90;
    public static final int MAX_DAYS = 365;
    private final StudyAttemptAnalytics analytics;
    private final ZoneId zone;
    private final Clock clock;

    public QueryHeatmapUseCase(StudyAttemptAnalytics analytics, ZoneId zone, Clock clock) {
        this.analytics = analytics;
        this.zone = zone;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<HeatmapDay> execute(UUID userId, Integer days) {
        int requested = days == null ? DEFAULT_DAYS : days;
        if (requested < 1 || requested > MAX_DAYS) {
            throw new IllegalArgumentException("days phải trong 1.." + MAX_DAYS);
        }
        LocalDate through = LocalDate.now(clock.withZone(zone));
        return analytics.heatmap(userId, through.minusDays(requested - 1L), through, zone);
    }
}
