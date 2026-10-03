package com.knowledgegym.content.application;

import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.content.domain.model.Topic;
import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.content.domain.port.TopicRepository;
import com.knowledgegym.shared.application.ConflictException;
import com.knowledgegym.shared.application.NotFoundException;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Explicit ID-based admin mutations; import's slug-based upsert is deliberately not used. */
public class ManageCatalogUseCase {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private final TopicRepository topics;
    private final ModuleRepository modules;
    public ManageCatalogUseCase(TopicRepository topics, ModuleRepository modules) {
        this.topics = topics; this.modules = modules;
    }
    public record TopicPatch(String name, String slug, String description, Integer displayOrder) {}
    public record ModulePatch(UUID topicId, String name, String slug, String description, Integer displayOrder) {}

    @Transactional public Topic createTopic(TopicPatch patch) {
        Topic topic = new Topic(name(patch.name()), slug(patch.slug()), order(patch.displayOrder()));
        topic.setDescription(patch.description());
        checkTopicSlug(topic.getSlug(), null);
        return topics.save(topic);
    }
    @Transactional public Topic updateTopic(UUID id, TopicPatch patch) {
        Topic topic = topics.findById(id).orElseThrow(() -> new NotFoundException("Topic không tồn tại: " + id));
        if (patch.name() != null) topic.setName(name(patch.name()));
        if (patch.slug() != null) { topic.setSlug(slug(patch.slug())); checkTopicSlug(topic.getSlug(), id); }
        if (patch.description() != null) topic.setDescription(patch.description());
        if (patch.displayOrder() != null) topic.setDisplayOrder(order(patch.displayOrder()));
        return topics.save(topic);
    }
    @Transactional public void deleteTopic(UUID id) {
        topics.findById(id).orElseThrow(() -> new NotFoundException("Topic không tồn tại: " + id));
        if (!modules.findByTopicIdOrdered(id).isEmpty()) throw new ConflictException("Topic còn module");
        topics.deleteById(id);
    }
    @Transactional public ModuleRef createModule(ModulePatch patch) {
        requireTopic(patch.topicId());
        ModuleRef module = new ModuleRef(name(patch.name()), slug(patch.slug()), order(patch.displayOrder()));
        module.setTopicId(patch.topicId()); module.setDescription(patch.description());
        checkModuleSlug(module.getSlug(), null);
        return modules.save(module);
    }
    @Transactional public ModuleRef updateModule(UUID id, ModulePatch patch) {
        ModuleRef module = modules.findById(id).orElseThrow(() -> new NotFoundException("Module không tồn tại: " + id));
        if (patch.topicId() != null) { requireTopic(patch.topicId()); module.setTopicId(patch.topicId()); }
        if (patch.name() != null) module.setName(name(patch.name()));
        if (patch.slug() != null) { module.setSlug(slug(patch.slug())); checkModuleSlug(module.getSlug(), id); }
        if (patch.description() != null) module.setDescription(patch.description());
        if (patch.displayOrder() != null) module.setDisplayOrder(order(patch.displayOrder()));
        return modules.save(module);
    }
    @Transactional public void deleteModule(UUID id) {
        modules.findById(id).orElseThrow(() -> new NotFoundException("Module không tồn tại: " + id));
        if (modules.countQuestions(id) > 0) throw new ConflictException("Module còn câu hỏi");
        modules.deleteById(id);
    }
    private void requireTopic(UUID id) {
        if (id == null) throw new IllegalArgumentException("topicId bắt buộc");
        topics.findById(id).orElseThrow(() -> new NotFoundException("Topic không tồn tại: " + id));
    }
    private void checkTopicSlug(String slug, UUID id) {
        topics.findBySlug(slug).ifPresent(t -> { if (!t.getId().equals(id)) throw new ConflictException("slug topic đã tồn tại"); });
    }
    private void checkModuleSlug(String slug, UUID id) {
        modules.findBySlug(slug).ifPresent(m -> { if (!m.getId().equals(id)) throw new ConflictException("slug module đã tồn tại"); });
    }
    private static String name(String value) {
        if (value == null || value.isBlank() || value.length() > 200) throw new IllegalArgumentException("name phải có 1-200 ký tự");
        return value.trim();
    }
    private static String slug(String value) {
        if (value == null || value.length() > 200 || !SLUG.matcher(value).matches()) throw new IllegalArgumentException("slug không hợp lệ");
        return value;
    }
    private static int order(Integer value) {
        if (value == null || value < 0) throw new IllegalArgumentException("displayOrder phải >= 0");
        return value;
    }
}
