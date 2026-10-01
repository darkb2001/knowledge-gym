package com.knowledgegym.progress.domain.model;

import java.util.UUID;

public record LeaderboardUser(int rank, UUID userId, String displayName, int xp) {}
