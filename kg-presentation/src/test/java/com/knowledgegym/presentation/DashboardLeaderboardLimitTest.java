package com.knowledgegym.presentation;

import com.knowledgegym.presentation.rest.dashboard.DashboardController;
import com.knowledgegym.progress.application.QueryLeaderboardUseCase;
import com.knowledgegym.progress.domain.model.LeaderboardUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DashboardLeaderboardLimitTest {
    private final DashboardController controller = new DashboardController(null, null,
            new QueryLeaderboardUseCase(() -> IntStream.rangeClosed(1, 100)
                    .mapToObj(rank -> new LeaderboardUser(rank, UUID.randomUUID(), "Learner " + rank, 1000 - rank))
                    .toList()));

    @Test
    void defaultsToTopTen() throws Exception {
        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/dashboard/leaderboard"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].rank").value(1)).andExpect(jsonPath("$[9].rank").value(10));
    }

    @Test
    void respectsSmallerLimitAndBoundsOversizedOrNegativeRequests() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/dashboard/leaderboard").param("limit", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/dashboard/leaderboard").param("limit", "10000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(10));
        mvc.perform(get("/dashboard/leaderboard").param("limit", "-2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }
}
