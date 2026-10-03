package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.infrastructure.persistence.entity.ModuleJpaEntity;
import com.knowledgegym.infrastructure.persistence.repository.SpringDataModuleRepository;
import com.knowledgegym.infrastructure.persistence.repository.SpringDataQuestionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ModuleRepositoryAdapter implements ModuleRepository {

    private final SpringDataModuleRepository springData;
    private final SpringDataQuestionRepository questionRepository;

    public ModuleRepositoryAdapter(SpringDataModuleRepository springData,
                                   SpringDataQuestionRepository questionRepository) {
        this.springData = springData;
        this.questionRepository = questionRepository;
    }

    @Override
    public Optional<ModuleRef> findBySlug(String slug) {
        return springData.findBySlug(slug).map(ModuleRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<ModuleRef> findById(UUID id) {
        return springData.findById(id).map(ModuleRepositoryAdapter::toDomain);
    }

    @Override public ModuleRef save(ModuleRef module) {
        ModuleJpaEntity entity = module.getId() == null ? new ModuleJpaEntity() : springData.findById(module.getId()).orElseThrow();
        if (entity.getId() == null) entity.setId(UUID.randomUUID());
        entity.setTopicId(module.getTopicId());
        entity.setName(module.getName());
        entity.setSlug(module.getSlug());
        entity.setDescription(module.getDescription());
        entity.setDisplayOrder(module.getDisplayOrder());
        entity.setActive(module.isActive());
        return toDomain(springData.saveAndFlush(entity));
    }

    @Override public void deleteById(UUID id) { springData.deleteById(id); springData.flush(); }

    @Override
    public ModuleRef saveOrUpdateBySlug(ModuleRef module) {
        ModuleJpaEntity entity = springData.findBySlug(module.getSlug()).orElseGet(() -> {
            ModuleJpaEntity created = new ModuleJpaEntity();
            created.setId(UUID.randomUUID());
            return created;
        });
        entity.setTopicId(module.getTopicId());
        entity.setName(module.getName());
        entity.setSlug(module.getSlug());
        entity.setDescription(module.getDescription());
        entity.setDisplayOrder(module.getDisplayOrder());
        entity.setActive(module.isActive());
        return toDomain(springData.save(entity));
    }

    @Override
    public List<ModuleRef> findByTopicIdOrdered(UUID topicId) {
        return springData.findByTopicId(topicId).stream()
                .sorted(java.util.Comparator.comparingInt(ModuleJpaEntity::getDisplayOrder))
                .map(ModuleRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<ModuleRef> findAllOrdered() {
        return springData.findAll(Sort.by("displayOrder")).stream()
                .map(ModuleRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public long countQuestions(UUID moduleId) {
        return questionRepository.countByModuleId(moduleId);
    }

    @Override
    public Map<UUID, Long> countQuestionsByModule() {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : questionRepository.countGroupedByModule()) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    static ModuleRef toDomain(ModuleJpaEntity entity) {
        ModuleRef module = new ModuleRef(entity.getName(), entity.getSlug(), entity.getDisplayOrder());
        module.setId(entity.getId());
        module.setTopicId(entity.getTopicId());
        module.setDescription(entity.getDescription());
        module.setActive(entity.isActive());
        return module;
    }
}
