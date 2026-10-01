package com.knowledgegym.content.application;

import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.QuestionOption;
import com.knowledgegym.content.domain.model.QuestionQuery;
import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.content.domain.port.QuestionOptionRepository;
import com.knowledgegym.content.domain.service.DistractorGenerator;
import com.knowledgegym.shared.application.NotFoundException;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Import hook and admin backfill share one deterministic option-generation path. */
public class GenerateQuestionOptionsUseCase {
    private final ModuleRepository modules;
    private final QuestionRepository questions;
    private final QuestionOptionRepository options;

    public GenerateQuestionOptionsUseCase(ModuleRepository modules, QuestionRepository questions,
                                          QuestionOptionRepository options) {
        this.modules = modules;
        this.questions = questions;
        this.options = options;
    }

    public record Result(int questions, int eligible, int options) {}

    /**
     * Sinh option cho **đúng một** câu vừa tạo/sửa qua admin và trả về option của câu đó để response
     * admin phản ánh trạng thái sau ghi. Admin CRUD không đi qua `execute()` (backfill toàn bộ) nên
     * thiếu đường này thì câu mới tạo có `options = []` và bị loại khỏi quiz tới khi có người chạy tay
     * backfill — không nhất quán với import.
     *
     * <p><b>Cố ý không sinh lại câu anh em:</b> `replaceOptions` xoá rồi chèn lại option bằng UUID mới,
     * mà `quiz_answers.selected_option_id` là `ON DELETE SET NULL`. Sinh lại cả module chỉ vì admin sửa
     * 1 câu sẽ làm mất `selected_option_id` của mọi quiz cũ trong module đó.
     */
    @Transactional
    public List<QuestionOption> generateFor(UUID moduleId, UUID questionId) {
        var module = modules.findById(moduleId)
                .orElseThrow(() -> new NotFoundException("Module không tồn tại: " + moduleId));
        Question target = questions.findById(questionId)
                .orElseThrow(() -> new NotFoundException("Câu hỏi không tồn tại: " + questionId));

        List<Question> siblings = searchAll(moduleId).stream()
                .filter(question -> !question.getId().equals(questionId))
                .toList();
        List<Question> fallback = new ArrayList<>();
        for (ModuleRef topicModule : modules.findByTopicIdOrdered(module.getTopicId())) {
            if (!topicModule.getId().equals(moduleId)) {
                fallback.addAll(searchAll(topicModule.getId()));
            }
        }

        options.replaceOptions(questionId, DistractorGenerator.generate(target, siblings, fallback));
        return options.findByQuestionId(questionId);
    }

    /** Toàn bộ câu của 1 module, lặp theo `MAX_SIZE` để module > 100 câu không bị cắt im lặng. */
    private List<Question> searchAll(UUID moduleId) {
        List<Question> all = new ArrayList<>();
        int page = 1;
        while (true) {
            var part = questions.search(new QuestionQuery(moduleId, null, null, null,
                    page++, QuestionQuery.MAX_SIZE)).items();
            all.addAll(part);
            if (part.size() < QuestionQuery.MAX_SIZE) {
                break;
            }
        }
        return all;
    }

    @Transactional
    public Result execute() {
        var catalog = modules.findAllOrdered();
        Map<UUID, List<Question>> byModule = new LinkedHashMap<>();
        Map<UUID, List<Question>> byTopic = new LinkedHashMap<>();
        for (var module : catalog) {
            List<Question> pool = new ArrayList<>();
            int page = 1;
            while (true) {
                var part = questions.search(new QuestionQuery(module.getId(), null, null, null,
                        page++, QuestionQuery.MAX_SIZE)).items();
                pool.addAll(part);
                if (part.size() < QuestionQuery.MAX_SIZE) break;
            }
            byModule.put(module.getId(), pool);
            byTopic.computeIfAbsent(module.getTopicId(), key -> new ArrayList<>()).addAll(pool);
        }

        int count = 0;
        int eligible = 0;
        int total = 0;
        for (var module : catalog) {
            for (var question : byModule.get(module.getId())) {
                var generated = DistractorGenerator.generate(question, byModule.get(module.getId()),
                        byTopic.get(module.getTopicId()));
                count++;
                if (!generated.isEmpty()) eligible++;
                total += options.replaceOptions(question.getId(), generated);
            }
        }
        return new Result(count, eligible, total);
    }
}
