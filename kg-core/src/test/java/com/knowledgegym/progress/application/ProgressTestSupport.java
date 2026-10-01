package com.knowledgegym.progress.application;

import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.learning.domain.model.StudyAttempt;
import com.knowledgegym.learning.domain.port.StudyAttemptRepository;
import com.knowledgegym.progress.domain.model.UserProgress;
import com.knowledgegym.progress.domain.model.UserXpRow;
import com.knowledgegym.progress.domain.port.QuestionModulePort;
import com.knowledgegym.progress.domain.port.UserProgressRepository;
import com.knowledgegym.progress.domain.port.UserXpRepository;
import com.knowledgegym.progress.domain.service.UserXpPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Fakes cho m7 — không Spring/DB, chạy trong vài ms.
 *
 * <p>Cố ý bám sát ngữ nghĩa SQL thật thay vì "dễ dùng":
 * <ul>
 *   <li>{@code InMemoryProgressRepository} cộng dồn và tính `mastery_pct` từ **tổng luỹ kế** đúng như
 *       `ON CONFLICT DO UPDATE`. Nếu fake chỉ ghi đè bằng delta, test "2 lần ghi" sẽ xanh dù SQL sai.
 *   <li>{@code LockRecordingXpRepository} ghi lại thứ tự gọi {@code lockQuestion} để test khoá được
 *       lấy trên question id **đã sort** (chống deadlock).
 *   <li>`firstAttempts` và `xpOf` **recompute từ danh sách attempt** với đúng
 *       `ORDER BY question_id, attempted_at, id` như SQL (`UserXpAdapter.xpOf`), nên test
 *       "định nghĩa khớp" đo được lệch thật giữa đường ghi và đường đọc lại, không phải tautology.
 * </ul>
 */
public final class ProgressTestSupport {

    private ProgressTestSupport() {
    }

    public static ModuleRef module(UUID id, String slug) {
        ModuleRef module = new ModuleRef(slug, slug, 1);
        module.setId(id);
        return module;
    }

    /** Cộng dồn như `ON CONFLICT (user_id, module_id) DO UPDATE`, gồm công thức mastery luỹ kế. */
    public static final class InMemoryProgressRepository implements UserProgressRepository {
        final Map<String, UserProgress> store = new LinkedHashMap<>();
        int upsertCalls;

        private static String key(UUID userId, UUID moduleId) {
            return userId + ":" + moduleId;
        }

        @Override
        public Optional<UserProgress> find(UUID userId, UUID moduleId) {
            return Optional.ofNullable(store.get(key(userId, moduleId)));
        }

        @Override
        public List<UserProgress> findAllByUser(UUID userId) {
            return store.values().stream().filter(p -> p.userId().equals(userId)).toList();
        }

        @Override
        public void upsertAdding(UUID userId, UUID moduleId, int attemptsDelta, int correctDelta,
                                 Instant activeAt) {
            upsertCalls++;
            UserProgress existing = store.get(key(userId, moduleId));
            int total = (existing == null ? 0 : existing.totalAttempts()) + attemptsDelta;
            int correct = (existing == null ? 0 : existing.correctCount()) + correctDelta;
            BigDecimal mastery = total == 0
                    ? BigDecimal.ZERO.setScale(2)
                    : BigDecimal.valueOf(correct).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            Instant lastActive = existing == null || existing.lastActiveAt() == null
                    ? activeAt
                    : (existing.lastActiveAt().isAfter(activeAt) ? existing.lastActiveAt() : activeAt);
            store.put(key(userId, moduleId),
                    new UserProgress(userId, moduleId, mastery, total, correct, lastActive));
        }
    }

    public static final class InMemoryQuestionModulePort implements QuestionModulePort {
        final Map<UUID, UUID> resolved = new LinkedHashMap<>();

        public void seed(UUID questionId, UUID moduleId) {
            resolved.put(questionId, moduleId);
        }

        @Override
        public Map<UUID, UUID> moduleByQuestion(Collection<UUID> questionIds) {
            Map<UUID, UUID> result = new LinkedHashMap<>();
            questionIds.forEach(id -> {
                if (resolved.containsKey(id)) result.put(id, resolved.get(id));
            });
            return result;
        }
    }

    public static final class InMemoryStudyAttemptRepository implements StudyAttemptRepository {
        public final List<StudyAttempt> store = new ArrayList<>();

        @Override
        public StudyAttempt save(StudyAttempt attempt) {
            attempt.setId(attempt.getId() != null ? attempt.getId() : UUID.randomUUID());
            store.add(attempt);
            return attempt;
        }

        @Override
        public List<UUID> findWrongQuestionIdsByUser(UUID userId) {
            return store.stream()
                    .filter(a -> a.getUserId().equals(userId) && !a.isCorrect())
                    .map(StudyAttempt::getQuestionId).distinct().toList();
        }
    }

    /**
     * XP/lock fake đọc lại từ chính bảng attempt, đúng thứ tự tie-break của SQL thật.
     */
    public static final class XpAndLockRecorder implements UserXpRepository {
        private final List<StudyAttempt> attempts;
        final List<UUID> lockOrder = new ArrayList<>();
        final Map<UUID, Integer> xpByUser = new LinkedHashMap<>();
        final Map<UUID, String> displayNames = new LinkedHashMap<>();
        int addXpCalls;

        public XpAndLockRecorder(List<StudyAttempt> attempts) {
            this.attempts = attempts;
        }

        @Override
        public void lockQuestion(UUID userId, UUID questionId) {
            lockOrder.add(questionId);
        }

        @Override
        public Map<UUID, FirstAttempt> firstAttempts(UUID userId, Collection<UUID> questionIds) {
            Map<UUID, StudyAttempt> known = new LinkedHashMap<>();
            attempts.stream()
                    .filter(a -> a.getUserId().equals(userId))
                    .sorted(Comparator.comparing(StudyAttempt::getQuestionId, Comparator.comparing(UUID::toString))
                            .thenComparing(StudyAttempt::getAttemptedAt)
                            .thenComparing(a -> a.getId().toString()))
                    .forEach(a -> known.putIfAbsent(a.getQuestionId(), a));
            Map<UUID, FirstAttempt> result = new LinkedHashMap<>();
            questionIds.forEach(id -> {
                StudyAttempt first = known.get(id);
                if (first != null) result.put(id, new FirstAttempt(first.getId(), first.getAttemptedAt(),
                        first.getSource(), first.isCorrect()));
            });
            return result;
        }

        @Override
        public Map<UUID, String> displayNamesByIds(Collection<UUID> userIds) {
            Map<UUID, String> result = new LinkedHashMap<>();
            userIds.forEach(id -> {
                if (displayNames.containsKey(id)) result.put(id, displayNames.get(id));
            });
            return result;
        }

        @Override
        public void addXp(UUID userId, int delta) {
            addXpCalls++;
            xpByUser.merge(userId, delta, Integer::sum);
        }

        @Override
        public int currentXp(UUID userId) {
            return xpByUser.getOrDefault(userId, 0);
        }

        /** Bản sao của `UserXpAdapter.xpOf`: lần attempt đầu tiên của mỗi câu, tie-break theo id. */
        @Override
        public int xpOf(UUID userId) {
            Map<UUID, StudyAttempt> first = new LinkedHashMap<>();
            attempts.stream()
                    .filter(a -> a.getUserId().equals(userId))
                    .sorted(Comparator.comparing(StudyAttempt::getQuestionId, Comparator.comparing(UUID::toString))
                            .thenComparing(StudyAttempt::getAttemptedAt)
                            .thenComparing(a -> a.getId().toString()))
                    .forEach(a -> first.putIfAbsent(a.getQuestionId(), a));
            return first.values().stream()
                    .mapToInt(a -> UserXpPolicy.xpFor(a.getSource(), a.isCorrect()))
                    .sum();
        }

        @Override
        public List<UserXpRow> topByXp(int limit) {
            return xpByUser.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(limit)
                    .map(e -> new UserXpRow(e.getKey(), e.getValue()))
                    .toList();
        }
    }
}
