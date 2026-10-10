package com.knowledgegym.presentation.rest.english;

import com.knowledgegym.english.application.EnglishCatalog;
import com.knowledgegym.english.application.EnglishPracticeUseCase;
import com.knowledgegym.english.application.EnglishReferenceResponses;
import com.knowledgegym.english.domain.model.EnglishReferenceResponse;
import com.knowledgegym.english.domain.model.EnglishExercise;
import com.knowledgegym.english.domain.model.EnglishExercisePart;
import com.knowledgegym.english.domain.model.EnglishAttempt;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/english")
@PreAuthorize("isAuthenticated()")
public class EnglishPracticeController {
    private final EnglishCatalog catalog;
    private final EnglishPracticeUseCase practice;
    public EnglishPracticeController(EnglishCatalog catalog, EnglishPracticeUseCase practice) {
        this.catalog = catalog; this.practice = practice;
    }
    public record ItemView(String id, String stem, List<String> options) {}
    public record ExerciseView(String id, String skill, String title, String focus, int minutes, int minimumWords,
                               String prompt, String passage, String audioPath, List<ItemView> items, List<String> checklist,
                               List<EnglishExercisePart> parts, String scope, String curriculum) {}
    public record AnswerFeedback(String id, int correctIndex, String explanation) {}
    public record Feedback(Integer correct, int total, String transcript, List<AnswerFeedback> items,
                           EnglishReferenceResponse referenceResponse) {}
    public record AttemptView(UUID id, String exerciseId, String status, long version, Map<String, Integer> answers,
                              String response, int elapsedSeconds, Instant createdAt, Instant updatedAt, Feedback feedback) {}
    public record StartRequest(String exerciseId) {}
    public record SaveRequest(long version, Map<String, Integer> answers, String response, int elapsedSeconds, boolean submit) {}
    public record HistoryPage(List<AttemptView> items, long totalElements, int page, int size) {}

    @GetMapping("/exercises")
    public List<ExerciseView> exercises() { return catalog.list().stream().map(this::exerciseView).toList(); }
    /** Explicit learner-requested listening aid; keys/models remain submitted-attempt-only. */
    public record TranscriptView(String exerciseId, String partId, String text) {}
    @GetMapping("/exercises/{id}/transcript")
    public TranscriptView transcript(@PathVariable String id, @RequestParam(required=false) String partId) {
        var exercise = catalog.get(id);
        if (exercise.skill() != EnglishExercise.Skill.LISTENING)
            throw new IllegalArgumentException("Transcripts are available only for listening practice");
        if (partId == null || partId.equals(id)) return new TranscriptView(id, id, exercise.transcript());
        if (catalog.parts(id).stream().noneMatch(part -> part.id().equals(partId)))
            throw new IllegalArgumentException("Unknown listening section");
        return new TranscriptView(id, partId, catalog.get(partId).transcript());
    }
    @PostMapping("/attempts")
    public AttemptView start(@AuthenticationPrincipal UUID user, @RequestBody StartRequest request) {
        return attemptView(practice.start(user, request.exerciseId()));
    }
    @GetMapping("/attempts")
    public HistoryPage history(@AuthenticationPrincipal UUID user, @RequestParam(defaultValue="1") int page,
                               @RequestParam(defaultValue="10") int size) {
        var result = practice.list(user, page, size);
        return new HistoryPage(result.items().stream().map(this::attemptView).toList(), result.totalElements(), page, size);
    }
    @GetMapping("/attempts/{id}")
    public AttemptView get(@AuthenticationPrincipal UUID user, @PathVariable UUID id) { return attemptView(practice.get(user, id)); }
    @PutMapping("/attempts/{id}")
    public AttemptView save(@AuthenticationPrincipal UUID user, @PathVariable UUID id, @RequestBody SaveRequest request) {
        return attemptView(practice.save(user, id, request.version(), request.answers(), request.response(),
            request.elapsedSeconds(), request.submit()));
    }
    private ExerciseView exerciseView(EnglishExercise e) {
        return new ExerciseView(e.id(), e.skill().name(), e.title(), e.focus(), e.minutes(), e.minimumWords(),
            e.prompt(), e.passage(), e.audioPath(), e.items().stream().map(q -> new ItemView(q.id(), q.stem(), q.options())).toList(),
            e.checklist(), catalog.parts(e.id()), catalog.scope(e.id()), catalog.curriculum(e.id()));
    }
    private AttemptView attemptView(EnglishAttempt a) {
        Feedback feedback = null;
        if ("SUBMITTED".equals(a.status())) {
            var e = catalog.get(a.exerciseId());
            Integer correct = e.items().isEmpty() ? null : (int)e.items().stream()
                .filter(q -> Integer.valueOf(q.correctIndex()).equals(a.answers().get(q.id()))).count();
            feedback = new Feedback(correct, e.items().size(), e.transcript(),
                e.items().stream().map(q -> new AnswerFeedback(q.id(), q.correctIndex(), q.explanation())).toList(),
                EnglishReferenceResponses.find(e.id()).orElse(null));
        }
        return new AttemptView(a.id(), a.exerciseId(), a.status(), a.version(), a.answers(), a.response(),
            a.elapsedSeconds(), a.createdAt(), a.updatedAt(), feedback);
    }
}
