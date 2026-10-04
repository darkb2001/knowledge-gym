package com.knowledgegym.presentation.rest.interview.dto;

import com.knowledgegym.learning.application.MockInterviewUseCase;
import com.knowledgegym.learning.domain.model.InterviewSession;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response shapes cho `/mock-interview/*` — tách khỏi domain model để đổi nội bộ không phá client. */
public final class InterviewResponses {

    private InterviewResponses() {
    }

    public record QuestionPrompt(UUID questionId, String title) {

        public static QuestionPrompt of(MockInterviewUseCase.QuestionPrompt prompt) {
            return new QuestionPrompt(prompt.questionId(), prompt.title());
        }
    }

    public record Session(UUID id, UUID userId, UUID topicId, int questionCount, String mode, String status,
                          Instant startedAt, Instant finishedAt,
                          List<UUID> questionIds) {

        public static Session of(InterviewSession session) {
            return new Session(session.id(), session.userId(), session.topicId(), session.questionCount(),
                    session.mode(), session.status(), session.startedAt(),
                    session.finishedAt(), session.questionIds());
        }
    }

    public record Started(Session session, List<QuestionPrompt> questions) {

        public static Started of(MockInterviewUseCase.Started started) {
            return new Started(Session.of(started.session()),
                    started.questions().stream().map(QuestionPrompt::of).toList());
        }
    }

    /** 1 dòng của trang kết quả; `userAnswer` null = user chưa trả lời câu này. */
    public record ResultItem(UUID questionId, String title, String userAnswer, String answerHtml,
                             Instant answeredAt) {

        public static ResultItem of(MockInterviewUseCase.ResultItem item) {
            return new ResultItem(item.questionId(), item.title(), item.userAnswer(), item.answerHtml(),
                    item.answeredAt());
        }
    }

    /** Trang kết quả submit toàn cục: đủ mọi câu của phiên + số câu đã trả lời. */
    public record Result(Session session, List<ResultItem> items, int answeredCount) {

        public static Result of(MockInterviewUseCase.Result result) {
            return new Result(Session.of(result.session()), result.items().stream().map(ResultItem::of).toList(),
                    result.answeredCount());
        }
    }
}
