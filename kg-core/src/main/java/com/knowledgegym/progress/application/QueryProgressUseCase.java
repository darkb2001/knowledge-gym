package com.knowledgegym.progress.application;

import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.progress.domain.port.StudyAttemptAnalytics;
import com.knowledgegym.progress.domain.port.UserProgressRepository;
import com.knowledgegym.progress.domain.port.UserXpRepository;
import com.knowledgegym.progress.domain.service.StreakCalculator;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class QueryProgressUseCase {
    private final UserProgressRepository progress;
    private final UserXpRepository xp;
    private final StudyAttemptAnalytics analytics;
    private final ModuleRepository modules;
    private final ZoneId zone;
    private final Clock clock;

    public QueryProgressUseCase(UserProgressRepository progress, UserXpRepository xp,
                                StudyAttemptAnalytics analytics, ModuleRepository modules,
                                ZoneId zone, Clock clock) {
        this.progress = progress;
        this.xp = xp;
        this.analytics = analytics;
        this.modules = modules;
        this.zone = zone;
        this.clock = clock;
    }

    public record RadarModule(UUID moduleId, String name, java.math.BigDecimal masteryPct) {}
    public record ModuleProgress(UUID moduleId, java.math.BigDecimal masteryPct,
                                 int totalAttempts, int streak) {}
    public record Stats(int xp, int currentStreak, Integer longestStreak,
                        Integer level, List<String> badges) {}

    @Transactional(readOnly = true)
    public List<RadarModule> radar(UUID userId) {
        Map<UUID, String> names = modules.findAllOrdered().stream()
                .collect(Collectors.toMap(m -> m.getId(), m -> m.getName(), (a, b) -> a));
        return progress.findAllByUser(userId).stream()
                .map(item -> new RadarModule(item.moduleId(), names.getOrDefault(item.moduleId(), "Module"),
                        item.masteryPct())).toList();
    }

    @Transactional(readOnly = true)
    public List<ModuleProgress> modules(UUID userId) {
        Map<UUID, List<LocalDate>> dates = analytics.activeDatesByModule(userId, zone);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        return progress.findAllByUser(userId).stream()
                .map(item -> new ModuleProgress(item.moduleId(), item.masteryPct(), item.totalAttempts(),
                        StreakCalculator.current(dates.getOrDefault(item.moduleId(), List.of()), today)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Stats stats(UUID userId) {
        int streak = StreakCalculator.current(analytics.activeDates(userId, zone),
                LocalDate.now(clock.withZone(zone)));
        return new Stats(xp.currentXp(userId), streak, null, null, List.of());
    }
}
