package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.infrastructure.persistence.entity.SrsDeckJpaEntity;
import com.knowledgegym.infrastructure.persistence.repository.SpringDataSrsDeckRepository;
import com.knowledgegym.learning.domain.model.SrsDeck;
import com.knowledgegym.learning.domain.port.SrsDeckRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SrsDeckRepositoryAdapter implements SrsDeckRepository {

    private final SpringDataSrsDeckRepository springData;

    public SrsDeckRepositoryAdapter(SpringDataSrsDeckRepository springData) {
        this.springData = springData;
    }

    @Override
    public SrsDeck save(SrsDeck deck) {
        SrsDeckJpaEntity entity = deck.getId() == null
                ? new SrsDeckJpaEntity()
                : springData.findById(deck.getId()).orElseGet(SrsDeckJpaEntity::new);
        if (entity.getId() == null) {
            entity.setId(deck.getId() != null ? deck.getId() : UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
        }
        entity.setUserId(deck.getUserId());
        entity.setName(deck.getName());
        entity.setModuleId(deck.getModuleId());
        entity.setCustom(deck.isCustom());
        return toDomain(springData.save(entity));
    }

    @Override
    public Optional<SrsDeck> findByUserIdAndModuleId(UUID userId, UUID moduleId) {
        return springData.findByUserIdAndModuleId(userId, moduleId).map(SrsDeckRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<SrsDeck> findByIdAndUserId(UUID id, UUID userId) {
        return springData.findByIdAndUserId(id, userId).map(SrsDeckRepositoryAdapter::toDomain);
    }

    @Override
    public List<SrsDeck> findByUserId(UUID userId) {
        return springData.findByUserId(userId).stream()
                .map(SrsDeckRepositoryAdapter::toDomain)
                .toList();
    }

    /** `rehydrate` validate invariant `is_custom == (module_id == null)` — row lệch nổ khi đọc. */
    static SrsDeck toDomain(SrsDeckJpaEntity entity) {
        return SrsDeck.rehydrate(entity.getId(), entity.getUserId(), entity.getName(),
                entity.getModuleId(), entity.isCustom());
    }
}
