package com.knowledgegym.content.application;

import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.port.AnswerHtmlSanitizer;
import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.shared.application.ConflictException;
import com.knowledgegym.shared.application.NotFoundException;
import com.knowledgegym.shared.domain.model.Difficulty;

import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD câu hỏi — mọi method ở đây được gọi sau `@PreAuthorize("hasRole('ADMIN')")`
 * ở controller; use case không tự check quyền (không biết gì về Security context).
 *
 * Hai invariant được enforce ở đây, không phải ở controller:
 * 1. `answerHtml` luôn qua {@link AnswerHtmlSanitizer} — admin không được phép lưu `<script>`
 *    (nếu chỉ sanitize ở import, `GET /questions/{id}` sẽ trả XSS nguyên văn).
 * 2. `searchKeywords` được tính lại mỗi lần title/answer/tags đổi — nếu không, `searchable_text`
 *    giữ nội dung cũ sau khi sửa và search trả kết quả lệch với dữ liệu.
 */
public class ManageQuestionsUseCase {

    private final QuestionRepository questionRepository;
    private final ModuleRepository moduleRepository;
    private final AnswerHtmlSanitizer sanitizer;

    public ManageQuestionsUseCase(QuestionRepository questionRepository,
                                  ModuleRepository moduleRepository,
                                  AnswerHtmlSanitizer sanitizer) {
        this.questionRepository = questionRepository;
        this.moduleRepository = moduleRepository;
        this.sanitizer = sanitizer;
    }

    public record CreateCommand(UUID moduleId, String title, String answerHtml,
                                Difficulty difficulty, List<String> tags, Integer sortOrder) {}

    public record UpdateCommand(String title, String answerHtml, Difficulty difficulty, List<String> tags,
                                UUID moduleId, Integer sortOrder, List<String> searchKeywords) {
        public UpdateCommand(String title, String answerHtml, Difficulty difficulty, List<String> tags) {
            this(title, answerHtml, difficulty, tags, null, null, null);
        }
    }

    public Question create(CreateCommand command) {
        moduleRepository.findById(command.moduleId())
                .orElseThrow(() -> new NotFoundException("Module không tồn tại: " + command.moduleId()));

        int sortOrder = command.sortOrder() != null
                ? command.sortOrder()
                : questionRepository.nextSortOrder(command.moduleId());

        // Reject thay vì upsert: (module_id, sort_order) là natural key của import. Nếu admin
        // gửi sortOrder trùng, upsert sẽ ghi đè câu của người khác và trả 201 — im lặng mất dữ liệu.
        if (command.sortOrder() != null
                && questionRepository.findByModuleIdAndSortOrder(command.moduleId(), sortOrder).isPresent()) {
            throw new ConflictException("sortOrder " + sortOrder + " đã tồn tại trong module này");
        }

        Question question = new Question();
        question.setModuleId(command.moduleId());
        question.setTitle(requireText(command.title(), "title"));
        question.setAnswerHtml(requireNonBlank(sanitizer.sanitize(requireText(command.answerHtml(), "answerHtml")),
                "answerHtml"));
        question.setDifficulty(command.difficulty() != null ? command.difficulty() : Difficulty.MID);
        question.setTags(command.tags());
        question.setSortOrder(sortOrder);
        question.setSearchKeywords(keywordsFor(question));
        return questionRepository.save(question);
    }

    public Question update(UUID id, UpdateCommand command) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Câu hỏi không tồn tại: " + id));

        UUID destination = command.moduleId() == null ? question.getModuleId() : command.moduleId();
        if (command.moduleId() != null) moduleRepository.findById(destination)
                .orElseThrow(() -> new NotFoundException("Module không tồn tại: " + destination));
        int order = command.sortOrder() == null
                ? (destination.equals(question.getModuleId()) ? question.getSortOrder() : questionRepository.nextSortOrder(destination))
                : command.sortOrder();
        if (order < 0) throw new IllegalArgumentException("sortOrder phải >= 0");
        if (!destination.equals(question.getModuleId()) || order != question.getSortOrder()) {
            questionRepository.findByModuleIdAndSortOrder(destination, order).ifPresent(other -> {
                if (!other.getId().equals(id)) throw new ConflictException("sortOrder đã tồn tại trong module");
            });
        }
        question.setModuleId(destination);
        question.setSortOrder(order);
        boolean contentChanged = false;
        if (command.title() != null) {
            question.setTitle(requireText(command.title(), "title"));
            contentChanged = true;
        }
        if (command.answerHtml() != null) {
            question.setAnswerHtml(requireNonBlank(
                    sanitizer.sanitize(requireText(command.answerHtml(), "answerHtml")), "answerHtml"));
            contentChanged = true;
        }
        if (command.difficulty() != null) {
            question.setDifficulty(command.difficulty());
        }
        if (command.tags() != null) {
            question.setTags(command.tags());
            contentChanged = true;
        }
        if (command.searchKeywords() != null) {
            if (command.searchKeywords().stream().anyMatch(k -> k == null || k.isBlank()))
                throw new IllegalArgumentException("searchKeywords không được chứa giá trị rỗng");
            question.setSearchKeywords(command.searchKeywords());
        } else if (contentChanged) {
            question.setSearchKeywords(keywordsFor(question));
        }
        return questionRepository.save(question);
    }

    public Question changeStatus(UUID actor, UUID id, Question.ContentStatus status, String reason) {
        if (actor == null || status == null || reason == null || reason.isBlank() || reason.length() > 500) throw new IllegalArgumentException("status/reason không hợp lệ");
        Question current = questionRepository.findById(id).orElseThrow(() -> new NotFoundException("Câu hỏi không tồn tại: " + id));
        if (status == Question.ContentStatus.PUBLISHED && (current.getAnswerHtml() == null || SearchText.stripHtml(current.getAnswerHtml()).isBlank())) throw new IllegalArgumentException("Câu hỏi chưa có đáp án");
        return questionRepository.updateContentStatus(id, status, actor, reason.trim());
    }

    public void delete(UUID id) {
        questionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Câu hỏi không tồn tại: " + id));
        questionRepository.deleteById(id);
    }

    /** Cùng công thức với import: cụm n-gram + synonym Việt–Anh từ title/answer/tags. */
    private static List<String> keywordsFor(Question question) {
        return SearchText.tokenList(SearchText.build(
                question.getTitle(),
                SearchText.stripHtml(question.getAnswerHtml()),
                String.join(" ", question.getTags())));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " không được để trống");
        }
        return value;
    }

    /** Sau sanitize, HTML chỉ toàn thẻ bị bỏ có thể trở thành rỗng — coi như thiếu nội dung. */
    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " không có nội dung hợp lệ sau khi lọc HTML");
        }
        return value;
    }
}
