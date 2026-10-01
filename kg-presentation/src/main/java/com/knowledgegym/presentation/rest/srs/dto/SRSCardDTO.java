package com.knowledgegym.presentation.rest.srs.dto;

import com.knowledgegym.learning.application.QueryDueUseCase;
import com.knowledgegym.learning.domain.model.SRSCard;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 1 thẻ đến hạn cho FE lật.
 *
 * <p>`answerHtml` ĐÃ sanitize (Jsoup ở import/admin) — FE vẫn chạy DOMPurify lần nữa
 * (defense-in-depth, cùng cách với `QuestionDetailDTO`).
 *
 * <p>`repetitions`/`easeFactor` trả về để FE hiển thị tiến độ thẻ; không có `userId` (không cần
 * và tránh lộ id không dùng).
 */
public record SRSCardDTO(UUID cardId,
                         UUID questionId,
                         UUID moduleId,
                         String moduleSlug,
                         String title,
                         String answerHtml,
                         String difficulty,
                         int repetitions,
                         double easeFactor,
                         int intervalDays,
                         LocalDate nextReview) {

    public static SRSCardDTO of(QueryDueUseCase.DueCard due) {
        SRSCard card = due.card();
        return new SRSCardDTO(
                card.getId(),
                card.getQuestionId(),
                due.question().getModuleId(),
                due.moduleSlug(),
                due.question().getTitle(),
                due.question().getAnswerHtml(),
                due.question().getDifficulty() == null ? null : due.question().getDifficulty().name(),
                card.getRepetitions(),
                card.getEaseFactor(),
                card.getIntervalDays(),
                card.getNextReview());
    }
}
