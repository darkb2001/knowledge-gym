package com.knowledgegym.presentation.rest.dashboard;

import com.knowledgegym.presentation.rest.dashboard.dto.DashboardResponses;
import com.knowledgegym.progress.application.QueryHeatmapUseCase;
import com.knowledgegym.progress.application.QueryLeaderboardUseCase;
import com.knowledgegym.progress.application.QueryProgressUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
public class DashboardController {
    private final QueryProgressUseCase progress;
    private final QueryHeatmapUseCase heatmap;
    private final QueryLeaderboardUseCase leaderboard;

    public DashboardController(QueryProgressUseCase progress, QueryHeatmapUseCase heatmap,
                               QueryLeaderboardUseCase leaderboard) {
        this.progress = progress;
        this.heatmap = heatmap;
        this.leaderboard = leaderboard;
    }

    @GetMapping("/dashboard/radar")
    public DashboardResponses.Radar radar(@AuthenticationPrincipal UUID userId) {
        return new DashboardResponses.Radar(progress.radar(userId).stream()
                .map(row -> new DashboardResponses.RadarModule(row.moduleId(), row.name(), row.masteryPct()))
                .toList());
    }

    @GetMapping("/dashboard/heatmap")
    public List<DashboardResponses.Day> heatmap(@AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "90") Integer days) {
        return heatmap.execute(userId, days).stream().map(DashboardResponses.Day::of).toList();
    }

    @GetMapping("/dashboard/leaderboard")
    public List<DashboardResponses.LeaderboardRow> leaderboard(
            @RequestParam(defaultValue = "10") int limit) {
        // Keep the shared top-100 Redis cache, but never send that entire cache to the dashboard.
        int boundedLimit = Math.max(1, Math.min(10, limit));
        return leaderboard.execute().stream().limit(boundedLimit)
                .map(DashboardResponses.LeaderboardRow::of).toList();
    }

    @GetMapping("/users/me/progress")
    public List<DashboardResponses.Progress> myProgress(@AuthenticationPrincipal UUID userId) {
        return progress.modules(userId).stream().map(DashboardResponses.Progress::of).toList();
    }

    @GetMapping("/users/me/stats")
    public DashboardResponses.Stats myStats(@AuthenticationPrincipal UUID userId) {
        return DashboardResponses.Stats.of(progress.stats(userId));
    }
}
