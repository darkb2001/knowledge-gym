package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.content.domain.model.ContentTrack;
import com.knowledgegym.content.domain.port.ContentTrackRepository;
import com.knowledgegym.infrastructure.persistence.entity.ContentTrackJpaEntity;
import com.knowledgegym.infrastructure.persistence.repository.SpringDataContentTrackRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ContentTrackRepositoryAdapter implements ContentTrackRepository {

    private final SpringDataContentTrackRepository springData;

    public ContentTrackRepositoryAdapter(SpringDataContentTrackRepository springData) {
        this.springData = springData;
    }

    @Override
    public ContentTrack saveOrUpdateBySlug(ContentTrack track) {
        ContentTrackJpaEntity entity = springData.findById(track.getSlug()).orElseGet(() -> {
            ContentTrackJpaEntity created = new ContentTrackJpaEntity();
            created.setSlug(track.getSlug());
            created.setCreatedAt(Instant.now());
            return created;
        });
        entity.setName(track.getName());
        entity.setDescription(track.getDescription());
        entity.setIcon(track.getIcon());
        entity.setDisplayOrder(track.getDisplayOrder());
        return toDomain(springData.save(entity));
    }

    @Override
    public List<ContentTrack> findAllOrdered() {
        return springData.findAll(Sort.by("displayOrder")).stream()
                .map(ContentTrackRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<ContentTrack> findBySlug(String slug) {
        return springData.findById(slug).map(ContentTrackRepositoryAdapter::toDomain);
    }

    static ContentTrack toDomain(ContentTrackJpaEntity entity) {
        return new ContentTrack(entity.getName(), entity.getSlug(), entity.getDisplayOrder(),
                entity.getDescription(), entity.getIcon());
    }
}
