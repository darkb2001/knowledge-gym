package com.knowledgegym.progress.application;

import com.knowledgegym.learning.domain.model.AttemptSource;
import com.knowledgegym.learning.domain.model.StudyAttempt;
import com.knowledgegym.learning.domain.port.StudyAttemptRepository;
import com.knowledgegym.progress.domain.port.QuestionModulePort;
import com.knowledgegym.progress.domain.port.UserProgressRepository;
import com.knowledgegym.progress.domain.port.UserXpRepository;
import com.knowledgegym.progress.domain.service.UserXpPolicy;
import com.knowledgegym.shared.application.NotFoundException;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** The only write path for attempts: it persists the fact and materializes progress + XP atomically. */
public class RecordAttemptUseCase {
    private final StudyAttemptRepository attempts;
    private final QuestionModulePort questionModules;
    private final UserXpRepository xp;
    private final UserProgressRepository progress;

    public RecordAttemptUseCase(StudyAttemptRepository attempts, QuestionModulePort questionModules,
                                UserXpRepository xp, UserProgressRepository progress) {
        this.attempts = attempts;
        this.questionModules = questionModules;
        this.xp = xp;
        this.progress = progress;
    }

    public record AttemptFact(UUID questionId, AttemptSource source, boolean correct, Integer timeMs,
                              String answer, BigDecimal score) {
        public AttemptFact(UUID questionId, AttemptSource source, boolean correct) {
            this(questionId, source, correct, null, null, null);
        }
    }

    @Transactional
    public void execute(UUID userId, List<AttemptFact> facts, Instant at) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(at, "at");
        if (facts == null || facts.isEmpty()) return;
        if (facts.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("facts chứa phần tử null");
        for (var fact : facts) {
            Objects.requireNonNull(fact.questionId(), "questionId");
            Objects.requireNonNull(fact.source(), "source");
            if (fact.timeMs() != null && fact.timeMs() < 0) throw new IllegalArgumentException("timeMs không được âm");
        }

        List<UUID> questionIds = facts.stream().map(AttemptFact::questionId).distinct()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        if (facts.stream().map(AttemptFact::questionId).distinct().count() != facts.size()) {
            throw new IllegalArgumentException("facts không được chứa câu hỏi trùng");
        }
        // Serialize the check-and-award decision for each user/question pair until commit.
        questionIds.forEach(questionId -> xp.lockQuestion(userId, questionId));
        Map<UUID, UserXpRepository.FirstAttempt> previousFirstAttempts = xp.firstAttempts(userId, questionIds);
        Map<UUID, UUID> moduleByQuestion = questionModules.moduleByQuestion(questionIds);
        if (questionIds.stream().anyMatch(id -> !moduleByQuestion.containsKey(id))) {
            throw new NotFoundException("Không tìm thấy module của câu hỏi trong attempt");
        }

        Map<UUID, StudyAttempt> attemptsByQuestion = new LinkedHashMap<>();
        List<StudyAttempt> recordedAttempts = new java.util.ArrayList<>();
        Map<UUID, int[]> totalsByModule = new LinkedHashMap<>();
        for (AttemptFact fact : facts) {
            StudyAttempt attempt = StudyAttempt.record(userId, fact.questionId(), fact.source(),
                    fact.correct(), fact.timeMs(), at);
            attempt.setAnswer(fact.answer());
            attempt.setScore(fact.score());
            attemptsByQuestion.put(fact.questionId(), attempt);
            recordedAttempts.add(attempt);
            int[] totals = totalsByModule.computeIfAbsent(moduleByQuestion.get(fact.questionId()), key -> new int[2]);
            totals[0]++;
            if (fact.correct()) totals[1]++;
        }
        int xpDelta = 0;
        for (var entry : attemptsByQuestion.entrySet()) {
            StudyAttempt candidate = entry.getValue();
            UserXpRepository.FirstAttempt previous = previousFirstAttempts.get(entry.getKey());
            int candidateXp = UserXpPolicy.xpFor(candidate.getSource(), candidate.isCorrect());
            if (previous == null) {
                xpDelta += candidateXp;
            } else if (compare(candidate.getAttemptedAt(), candidate.getId(),
                    previous.attemptedAt(), previous.attemptId()) < 0) {
                // Caller timestamps are preserved. A late-arriving backdated attempt can replace the
                // prior first-attempt XP, so adjust by the difference to keep the read model derivable.
                xpDelta += candidateXp - UserXpPolicy.xpFor(previous.source(), previous.correct());
            }
        }

        recordedAttempts.forEach(attempts::save);
        if (xpDelta != 0) xp.addXp(userId, xpDelta);
        for (var entry : totalsByModule.entrySet()) {
            progress.upsertAdding(userId, entry.getKey(), entry.getValue()[0], entry.getValue()[1], at);
        }
    }

    private static int compare(Instant leftAt, UUID leftId, Instant rightAt, UUID rightId) {
        int timestampOrder = leftAt.compareTo(rightAt);
        return timestampOrder != 0 ? timestampOrder : leftId.toString().compareTo(rightId.toString());
    }
}
