package com.knowledgegym.presentation.rest.interview;

import com.knowledgegym.learning.application.MockInterviewUseCase;
import com.knowledgegym.presentation.rest.content.dto.PageResponse;
import com.knowledgegym.presentation.rest.interview.dto.InterviewRequests;
import com.knowledgegym.presentation.rest.interview.dto.InterviewResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/mock-interview")
public class MockInterviewController {

    private final MockInterviewUseCase useCase;

    public MockInterviewController(MockInterviewUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewResponses.Started start(@AuthenticationPrincipal UUID user,
                                            @Valid @RequestBody InterviewRequests.Start request) {
        return InterviewResponses.Started.of(
                useCase.start(user, request.topicId(), request.questionCount(), request.mode()));
    }

    @PostMapping("/{id}/answer")
    public InterviewResponses.AnswerResult answer(@AuthenticationPrincipal UUID user, @PathVariable UUID id,
                                                  @Valid @RequestBody InterviewRequests.Answer request) {
        return InterviewResponses.AnswerResult.of(
                useCase.answer(user, id, request.questionId(), request.userAnswer()));
    }

    @PostMapping("/{id}/finish")
    public InterviewResponses.Session finish(@AuthenticationPrincipal UUID user, @PathVariable UUID id) {
        return InterviewResponses.Session.of(useCase.finish(user, id));
    }

    @GetMapping("/history")
    public PageResponse<InterviewResponses.Session> history(@AuthenticationPrincipal UUID user,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        var history = useCase.history(user, page, size);
        return new PageResponse<>(history.items().stream().map(InterviewResponses.Session::of).toList(),
                history.page(), history.size(), history.totalElements(), history.totalPages());
    }
}
