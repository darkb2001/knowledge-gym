package com.knowledgegym.presentation.rest.quiz;

import com.knowledgegym.learning.application.GenerateQuizUseCase;
import com.knowledgegym.learning.application.SubmitQuizUseCase;
import com.knowledgegym.learning.application.QueryQuizUseCase;
import com.knowledgegym.presentation.rest.quiz.dto.QuizRequests;
import com.knowledgegym.presentation.rest.quiz.dto.QuizResponses;
import com.knowledgegym.presentation.rest.content.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import java.util.UUID;

@RestController
@RequestMapping("/quiz")
public class QuizController {
    private final GenerateQuizUseCase generate;
    private final SubmitQuizUseCase submit;
    private final QueryQuizUseCase query;

    public QuizController(GenerateQuizUseCase generate, SubmitQuizUseCase submit, QueryQuizUseCase query) {
        this.generate = generate;
        this.submit = submit;
        this.query = query;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizResponses.Session generate(@AuthenticationPrincipal UUID user,
                                           @Valid @RequestBody QuizRequests.Generate request) {
        return QuizResponses.Session.of(generate.execute(user, new GenerateQuizUseCase.GenerateCommand(
                request.moduleId(), request.count(), request.strategy(), request.difficulty())));
    }

    @PostMapping("/{id}/submit")
    public SubmitQuizUseCase.QuizResult submit(@AuthenticationPrincipal UUID user, @PathVariable UUID id,
                                              @Valid @RequestBody QuizRequests.Submit request) {
        var answers = request.answers().stream().map(a -> new SubmitQuizUseCase.SubmittedAnswer(
                a.questionId(), a.selectedOptionId(), a.timeMs())).toList();
        return submit.execute(user, id, new SubmitQuizUseCase.SubmitCommand(answers));
    }

    @GetMapping("/history")
    public PageResponse<QuizResponses.Summary> history(@AuthenticationPrincipal UUID user,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var history = query.history(user, page, size);
        return new PageResponse<>(history.items().stream().map(QuizResponses.Summary::of).toList(),
                history.page(), history.size(), history.totalElements(), history.totalPages());
    }

    @GetMapping("/{id}")
    public QuizResponses.Session get(@AuthenticationPrincipal UUID user, @PathVariable UUID id) {
        return QuizResponses.Session.of(query.get(user, id));
    }
}
