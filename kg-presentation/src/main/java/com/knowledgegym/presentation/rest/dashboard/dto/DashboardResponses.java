package com.knowledgegym.presentation.rest.dashboard.dto;

import com.knowledgegym.progress.application.QueryProgressUseCase;
import com.knowledgegym.progress.domain.model.HeatmapDay;
import com.knowledgegym.progress.domain.model.LeaderboardUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Public response shapes for dashboard and current-user progress APIs. */
public final class DashboardResponses {
    private DashboardResponses() {}

    public record RadarModule(UUID moduleId, String name, BigDecimal masteryPct) {}
    public record Radar(List<RadarModule> modules) {}
    public record Day(LocalDate date, long count) {
        public static Day of(HeatmapDay day) { return new Day(day.date(), day.count()); }
    }
    public record Progress(UUID moduleId, BigDecimal masteryPct, int totalAttempts, int streak) {
        public static Progress of(QueryProgressUseCase.ModuleProgress row) {
            return new Progress(row.moduleId(), row.masteryPct(), row.totalAttempts(), row.streak());
        }
    }
    public record Stats(int xp, int currentStreak, Integer longestStreak, Integer level,
                        List<String> badges) {
        public static Stats of(QueryProgressUseCase.Stats stats) {
            return new Stats(stats.xp(), stats.currentStreak(), stats.longestStreak(),
                    stats.level(), stats.badges());
        }
    }
    public record LeaderboardRow(int rank, UUID userId, String displayName, int xp) {
        public static LeaderboardRow of(LeaderboardUser user) {
            return new LeaderboardRow(user.rank(), user.userId(), user.displayName(), user.xp());
        }
    }
}
