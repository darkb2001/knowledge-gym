package com.knowledgegym.progress.domain.port;

import com.knowledgegym.progress.domain.model.LeaderboardUser;
import java.util.List;

public interface LeaderboardPort {
    List<LeaderboardUser> topUsers();
}
