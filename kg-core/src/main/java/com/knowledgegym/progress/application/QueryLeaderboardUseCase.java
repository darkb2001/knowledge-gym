package com.knowledgegym.progress.application;

import com.knowledgegym.progress.domain.model.LeaderboardUser;
import com.knowledgegym.progress.domain.port.LeaderboardPort;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public class QueryLeaderboardUseCase {
    private final LeaderboardPort leaderboard;
    public QueryLeaderboardUseCase(LeaderboardPort leaderboard) { this.leaderboard = leaderboard; }

    @Transactional(readOnly = true)
    public List<LeaderboardUser> execute() { return leaderboard.topUsers(); }
}
