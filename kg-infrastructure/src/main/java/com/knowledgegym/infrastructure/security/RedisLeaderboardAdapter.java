package com.knowledgegym.infrastructure.security;

import com.knowledgegym.progress.domain.model.LeaderboardUser;
import com.knowledgegym.progress.domain.port.LeaderboardPort;
import com.knowledgegym.progress.domain.port.UserXpRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;

/** Rebuildable cache-aside leaderboard. PostgreSQL xp remains the source for each rebuild. */
@Component
public class RedisLeaderboardAdapter implements LeaderboardPort {
    private static final String KEY = "lb:global";
    private static final String EMPTY_KEY = "lb:global:empty";
    private static final Duration TTL = Duration.ofHours(1);
    private static final Duration EMPTY_TTL = Duration.ofMinutes(5);
    private static final int LIMIT = 100;
    private final StringRedisTemplate redis;
    private final UserXpRepository users;

    public RedisLeaderboardAdapter(StringRedisTemplate redis, UserXpRepository users) {
        this.redis = redis;
        this.users = users;
    }

    @Override
    public List<LeaderboardUser> topUsers() {
        List<RankedId> ranked = readCache();
        if (ranked == null) ranked = rebuild();
        if (ranked.isEmpty()) return List.of();
        Map<UUID, String> displayNames = users.displayNamesByIds(
                ranked.stream().map(RankedId::userId).toList());
        List<LeaderboardUser> result = new ArrayList<>();
        int rank = 1;
        for (RankedId entry : ranked) {
            String displayName = displayNames.get(entry.userId());
            if (displayName != null) result.add(new LeaderboardUser(rank++, entry.userId(), displayName, entry.xp()));
        }
        return List.copyOf(result);
    }

    /** null means cache miss; an empty list is a cached empty leaderboard. */
    private List<RankedId> readCache() {
        if (Boolean.TRUE.equals(redis.hasKey(KEY))) {
            Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet().reverseRangeWithScores(KEY, 0, LIMIT - 1);
            if (tuples == null) return List.of();
            List<RankedId> result = new ArrayList<>();
            for (var tuple : tuples) {
                if (tuple.getValue() == null) continue;
                RankedId decoded = decode(tuple.getValue());
                if (decoded != null) result.add(decoded);
            }
            return List.copyOf(result);
        }
        if (Boolean.TRUE.equals(redis.hasKey(EMPTY_KEY))) return List.of();
        return null;
    }

    private List<RankedId> rebuild() {
        List<RankedId> ranked = users.topByXp(LIMIT).stream()
                .map(row -> new RankedId(row.userId(), row.xp())).toList();
        if (ranked.isEmpty()) {
            // Redis has no persistent empty sorted set. This marker distinguishes an empty hit from a miss.
            redis.delete(KEY);
            redis.opsForValue().set(EMPTY_KEY, "1", EMPTY_TTL);
            return List.of();
        }

        String temporary = KEY + ":tmp:" + UUID.randomUUID();
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
            for (RankedId row : ranked) tuples.add(ZSetOperations.TypedTuple.of(encode(row), (double) row.xp()));
            redis.opsForZSet().add(temporary, tuples);
            redis.expire(temporary, Duration.ofSeconds(60));
            // RENAME atomically replaces the old snapshot. A unique temporary key makes concurrent misses safe.
            redis.rename(temporary, KEY);
            redis.expire(KEY, TTL);
            redis.delete(EMPTY_KEY);
        } finally {
            redis.delete(temporary);
        }
        return ranked;
    }

    static String encode(RankedId row) {
        return String.format(java.util.Locale.ROOT, "%010d:%s", row.xp(), row.userId());
    }

    static RankedId decode(String member) {
        if (member == null || member.length() < 47 || member.charAt(10) != ':') return null;
        try {
            return new RankedId(UUID.fromString(member.substring(11)), Integer.parseInt(member.substring(0, 10)));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    record RankedId(UUID userId, int xp) {}
}
