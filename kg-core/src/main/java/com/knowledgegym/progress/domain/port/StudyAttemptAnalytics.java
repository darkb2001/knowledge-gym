package com.knowledgegym.progress.domain.port;

import com.knowledgegym.progress.domain.model.HeatmapDay;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface StudyAttemptAnalytics {
    List<LocalDate> activeDates(UUID userId, ZoneId zone);
    Map<UUID, List<LocalDate>> activeDatesByModule(UUID userId, ZoneId zone);
    List<HeatmapDay> heatmap(UUID userId, LocalDate from, LocalDate through, ZoneId zone);
}
