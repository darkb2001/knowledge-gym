package com.knowledgegym.content.application;

import com.knowledgegym.content.domain.model.ContentCatalog;
import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.content.domain.model.ParsedQuestion;
import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.Topic;
import com.knowledgegym.content.domain.port.ContentSource;
import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.content.domain.port.TopicRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Import toàn bộ docs/ vào DB: topics → modules → questions.
 *
 * Thứ tự bắt buộc vì `questions.module_id` FK tới `modules.id` và `modules.topic_id` tới `topics.id`.
 * Idempotent theo natural key (topic/module = `slug`, question = `(module_id, sort_order)`).
 *
 * Re-import coi docs là source of truth cho các module có trong catalog lần này: sau upsert,
 * mọi câu hỏi của module đó mà sort_order không còn trong parse sẽ bị xoá (card bị bỏ/xoá
 * trong HTML không được để lại hàng stale).
 */
public class ImportContentUseCase {

    private GenerateQuestionOptionsUseCase generateOptions;
    private final ContentSource contentSource;
    private final TopicRepository topicRepository;
    private final ModuleRepository moduleRepository;
    private final QuestionRepository questionRepository;

    public ImportContentUseCase(ContentSource contentSource,
                                TopicRepository topicRepository,
                                ModuleRepository moduleRepository,
                                QuestionRepository questionRepository) {
        this.contentSource = contentSource;
        this.topicRepository = topicRepository;
        this.moduleRepository = moduleRepository;
        this.questionRepository = questionRepository;
    }

    public ImportContentUseCase(ContentSource c, TopicRepository t, ModuleRepository m, QuestionRepository q, GenerateQuestionOptionsUseCase o) {
        this(c,t,m,q);generateOptions=o;
    }
    public record ImportResult(int topics, int modules, int questions, int deleted,
                               List<String> orphanModuleSlugs) {}

    public ImportResult execute(String docsPath) {
        ContentCatalog catalog = contentSource.readCatalog(docsPath);

        Map<String, UUID> topicIdsBySlug = new HashMap<>();
        for (Topic parsed : catalog.topics()) {
            Topic saved = topicRepository.saveOrUpdateBySlug(parsed);
            topicIdsBySlug.put(saved.getSlug(), saved.getId());
        }

        Map<String, UUID> moduleIdsBySlug = new HashMap<>();
        for (ModuleRef parsed : catalog.modules()) {
            UUID topicId = topicIdsBySlug.get(parsed.getTopicSlug());
            if (topicId == null) {
                throw new ContentImportException("Module '" + parsed.getSlug()
                        + "' trỏ tới topic không có trong index.html: " + parsed.getTopicSlug());
            }
            parsed.setTopicId(topicId);
            ModuleRef saved = moduleRepository.saveOrUpdateBySlug(parsed);
            moduleIdsBySlug.put(saved.getSlug(), saved.getId());
        }

        List<Question> toPersist = new ArrayList<>(catalog.questions().size());
        List<String> orphans = new ArrayList<>();
        Map<UUID, Set<Integer>> keepSortOrdersByModule = new HashMap<>();
        for (UUID moduleId : moduleIdsBySlug.values()) {
            keepSortOrdersByModule.put(moduleId, new HashSet<>());
        }

        for (ParsedQuestion parsed : catalog.questions()) {
            UUID moduleId = moduleIdsBySlug.get(parsed.moduleSlug());
            if (moduleId == null) {
                orphans.add(parsed.moduleSlug());
                continue;
            }
            keepSortOrdersByModule.get(moduleId).add(parsed.sortOrder());
            toPersist.add(toEntity(parsed, moduleId));
        }

        int written = questionRepository.saveOrUpdateByNaturalKey(toPersist);

        // Chỉ đụng module có trong catalog lần này — module cũ không còn trong docs giữ nguyên.
        int deleted = 0;
        for (Map.Entry<UUID, Set<Integer>> entry : keepSortOrdersByModule.entrySet()) {
            deleted += questionRepository.deleteAbsentSortOrders(entry.getKey(), entry.getValue());
        }

        if(generateOptions != null) generateOptions.execute();
        return new ImportResult(topicIdsBySlug.size(), moduleIdsBySlug.size(), written, deleted,
                List.copyOf(orphans));
    }

    private static Question toEntity(ParsedQuestion parsed, UUID moduleId) {
        Question question = new Question();
        question.setModuleId(moduleId);
        question.setTitle(parsed.title());
        question.setAnswerHtml(parsed.answerHtml());
        question.setDifficulty(parsed.difficulty());
        question.setTags(parsed.tags());
        question.setSearchKeywords(parsed.searchKeywords());
        question.setSortOrder(parsed.sortOrder());
        return question;
    }
}
