package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.content.domain.model.Topic;
import com.knowledgegym.content.domain.port.TopicRepository;
import com.knowledgegym.infrastructure.persistence.entity.TopicJpaEntity;
import com.knowledgegym.infrastructure.persistence.repository.SpringDataTopicRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TopicRepositoryAdapter implements TopicRepository {

    private final SpringDataTopicRepository springData;

    public TopicRepositoryAdapter(SpringDataTopicRepository springData) {
        this.springData = springData;
    }

    @Override
    public Optional<Topic> findBySlug(String slug) {
        return springData.findBySlug(slug).map(TopicRepositoryAdapter::toDomain);
    }

    @Override public Optional<Topic> findById(UUID id) {
        return springData.findById(id).map(TopicRepositoryAdapter::toDomain);
    }

    @Override public Topic save(Topic topic) {
        TopicJpaEntity entity = topic.getId() == null ? new TopicJpaEntity() : springData.findById(topic.getId()).orElseThrow();
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
        }
        entity.setName(topic.getName());
        entity.setSlug(topic.getSlug());
        entity.setDescription(topic.getDescription());
        entity.setActive(topic.isActive());
        entity.setDisplayOrder(topic.getDisplayOrder());
        return toDomain(springData.saveAndFlush(entity));
    }

    @Override public void deleteById(UUID id) { springData.deleteById(id); springData.flush(); }

    @Override
    public Topic saveOrUpdateBySlug(Topic topic) {
        TopicJpaEntity entity = springData.findBySlug(topic.getSlug()).orElseGet(() -> {
            TopicJpaEntity created = new TopicJpaEntity();
            created.setId(UUID.randomUUID());
            created.setCreatedAt(Instant.now());
            return created;
        });
        entity.setName(topic.getName());
        entity.setSlug(topic.getSlug());
        entity.setDescription(topic.getDescription());
        entity.setActive(topic.isActive());
        entity.setDisplayOrder(topic.getDisplayOrder());
        return toDomain(springData.save(entity));
    }

    @Override
    public List<Topic> findAllOrdered() {
        return springData.findAll(Sort.by("displayOrder")).stream()
                .map(TopicRepositoryAdapter::toDomain)
                .toList();
    }

    static Topic toDomain(TopicJpaEntity entity) {
        Topic topic = new Topic(entity.getName(), entity.getSlug(), entity.getDisplayOrder());
        topic.setId(entity.getId());
        topic.setDescription(entity.getDescription());
        topic.setActive(entity.isActive());
        return topic;
    }
}
