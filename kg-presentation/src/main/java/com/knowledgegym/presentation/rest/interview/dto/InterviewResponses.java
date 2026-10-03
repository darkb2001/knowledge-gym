package com.knowledgegym.presentation.rest.interview.dto;

import com.knowledgegym.learning.application.MockInterviewUseCase;
import com.knowledgegym.learning.domain.model.InterviewAnswer;
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

    /** Kết quả 1 câu: trả `answerHtml` (đáp án mẫu) ngay khi submit — không chấm điểm. */
    public record AnswerResult(UUID sessionId, UUID questionId, String userAnswer, String answerHtml,
                               Instant attemptedAt) {

        public static AnswerResult of(InterviewAnswer answer) {
            return new AnswerResult(answer.sessionId(), answer.questionId(), answer.userAnswer(),
                    answer.answerHtml(), answer.attemptedAt());
        }
    }
}
