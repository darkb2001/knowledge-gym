package com.knowledgegym.learning.application;

import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.learning.domain.model.SRSCard;
import com.knowledgegym.learning.domain.port.SRSCardRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Danh sách thẻ đến hạn — nguồn dữ liệu cho phiên flashcard.
 *
 * <p>Đọc theo `idx_srs_due (user_id, next_review)` rồi sắp/lọc/giới hạn ở application: filter theo
 * module **không** đẩy xuống query được (module nằm ở `questions`, không phải `srs_cards`), và
 * `LIMIT` ở SQL sẽ cắt trước khi lọc module → trả thiếu thẻ. Thứ tự vì vậy là lọc module trước,
 * cắt limit sau, sắp theo `nextReview` để thẻ quá hạn lâu nhất hiện trước.
 *
 * <p>Thẻ "mồ côi" (câu hỏi đã bị re-import xoá) bị **bỏ qua** thay vì trả DTO thiếu nội dung —
 * re-import xoá câu là chuyện bình thường và FE không thể render thẻ rỗng.
 */
public class QueryDueUseCase {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private final SRSCardRepository cardRepository;
    private final QuestionRepository questionRepository;
    private final ModuleRepository moduleRepository;
    private final Clock clock;

    public QueryDueUseCase(SRSCardRepository cardRepository,
                           QuestionRepository questionRepository,
                           ModuleRepository moduleRepository,
                           Clock clock) {
        this.cardRepository = cardRepository;
        this.questionRepository = questionRepository;
        this.moduleRepository = moduleRepository;
        this.clock = clock;
    }

    /** Thẻ + nội dung câu hỏi để FE lật mặt sau. `answerHtml` đã sanitize từ lúc import/admin. */
    public record DueCard(SRSCard card, Question question, String moduleSlug) {
    }

    public List<DueCard> execute(UUID userId, UUID moduleId, Integer limit) {
        int effectiveLimit = limit == null || limit < 1 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        LocalDate today = LocalDate.now(clock);

        List<SRSCard> due = cardRepository.findDue(userId, today).stream()
                .sorted(Comparator.comparing(SRSCard::getNextReview))
                .toList();
        if (due.isEmpty()) {
            return List.of();
        }

        Map<UUID, Question> byId = questionRepository.findByIds(
                        due.stream().map(SRSCard::getQuestionId).collect(Collectors.toSet())).stream()
                .filter(question -> question.getContentStatus() == Question.ContentStatus.PUBLISHED)
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        List<SRSCard> selected = due.stream()
                .filter(card -> byId.containsKey(card.getQuestionId()))
                .filter(card -> moduleId == null || moduleId.equals(byId.get(card.getQuestionId()).getModuleId()))
                .limit(effectiveLimit)
                .toList();
        if (selected.isEmpty()) {
            return List.of();
        }

        // 1 query lấy slug cho mọi module trong kết quả — gọi findById từng thẻ là N+1 (tới 100 query).
        Map<UUID, String> slugById = moduleRepository.findAllOrdered().stream()
                .collect(Collectors.toMap(ModuleRef::getId, ModuleRef::getSlug, (a, b) -> a));

        return selected.stream()
                .map(card -> {
                    Question question = byId.get(card.getQuestionId());
                    return new DueCard(card, question, slugById.get(question.getModuleId()));
                })
                .toList();
    }
}
