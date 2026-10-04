package com.knowledgegym.content.application;

import com.knowledgegym.content.domain.model.ContentTrack;
import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.content.domain.model.ModuleWithStats;
import com.knowledgegym.content.domain.model.Topic;
import com.knowledgegym.content.domain.model.TopicWithStats;
import com.knowledgegym.content.domain.port.ContentTrackRepository;
import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.content.domain.port.TopicRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Đọc taxonomy (topics/modules) cho API public — không chứa logic ghi. */
public class CatalogQueryUseCase {

    private final TopicRepository topicRepository;
    private final ModuleRepository moduleRepository;
    private final ContentTrackRepository trackRepository;

    public CatalogQueryUseCase(TopicRepository topicRepository, ModuleRepository moduleRepository) {
        this(topicRepository, moduleRepository, null);
    }

    public CatalogQueryUseCase(TopicRepository topicRepository, ModuleRepository moduleRepository,
                               ContentTrackRepository trackRepository) {
        this.topicRepository = topicRepository;
        this.moduleRepository = moduleRepository;
        this.trackRepository = trackRepository;
    }

    /** Lộ trình học (Java / AWS …) theo display_order — UI gom topic theo track. */
    public List<ContentTrack> listTracks() {
        return trackRepository == null ? List.of() : trackRepository.findAllOrdered();
    }

    public List<TopicWithStats> listTopics() {
        List<Topic> topics = topicRepository.findAllOrdered();
        Map<UUID, Long> countByTopic = moduleRepository.findAllOrdered().stream()
                .collect(Collectors.groupingBy(ModuleRef::getTopicId, Collectors.counting()));
        return topics.stream()
                .map(topic -> new TopicWithStats(topic, countByTopic.getOrDefault(topic.getId(), 0L)))
                .toList();
    }

    public Optional<TopicWithStats> findTopicBySlug(String slug) {
        return topicRepository.findBySlug(slug)
                .map(topic -> new TopicWithStats(topic, moduleRepository.findByTopicIdOrdered(topic.getId()).size()));
    }

    public List<ModuleWithStats> listModules(UUID topicId) {
        List<ModuleRef> modules = topicId == null
                ? moduleRepository.findAllOrdered()
                : moduleRepository.findByTopicIdOrdered(topicId);

        Map<UUID, String> topicSlugById = topicRepository.findAllOrdered().stream()
                .collect(Collectors.toMap(Topic::getId, Topic::getSlug, (a, b) -> a));

        // Visible counts must match /questions; retain separate all-status counts for delete guards.
        Map<UUID, Long> countByModule = moduleRepository.countPublishedQuestionsByModule();

        return modules.stream()
                .map(module -> new ModuleWithStats(module,
                        topicSlugById.get(module.getTopicId()),
                        countByModule.getOrDefault(module.getId(), 0L)))
                .toList();
    }

    public Optional<ModuleWithStats> findModuleById(UUID id) {
        Map<UUID, String> topicSlugById = topicRepository.findAllOrdered().stream()
                .collect(Collectors.toMap(Topic::getId, Topic::getSlug, (a, b) -> a));
        return moduleRepository.findById(id)
                .map(module -> new ModuleWithStats(module,
                        topicSlugById.get(module.getTopicId()),
                        moduleRepository.countPublishedQuestions(module.getId())));
    }

    /** moduleId → slug, để list question không phải tra từng dòng. */
    public Map<UUID, String> moduleSlugIndex() {
        return moduleRepository.findAllOrdered().stream()
                .collect(Collectors.toMap(ModuleRef::getId, ModuleRef::getSlug, (a, b) -> a));
    }
}
