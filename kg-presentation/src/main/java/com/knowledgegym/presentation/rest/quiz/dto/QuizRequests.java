package com.knowledgegym.presentation.rest.quiz.dto;

import com.knowledgegym.learning.domain.model.QuizStrategy;
import com.knowledgegym.shared.domain.model.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public final class QuizRequests {
    private QuizRequests() {}
    public record Generate(@NotNull UUID moduleId, @Min(1) @Max(50) int count,
                           @NotNull QuizStrategy strategy, Difficulty difficulty) {}
    public record Answer(@NotNull UUID questionId, UUID selectedOptionId,
                         @PositiveOrZero Integer timeMs) {}
    public record Submit(@NotNull @Size(max = 50) List<@NotNull @Valid Answer> answers) {}
}
