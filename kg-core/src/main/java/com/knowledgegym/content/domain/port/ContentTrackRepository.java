package com.knowledgegym.content.domain.port;

import com.knowledgegym.content.domain.model.ContentTrack;

import java.util.List;
import java.util.Optional;

public interface ContentTrackRepository {

    /** Insert hoặc update theo `slug` (natural key) — idempotent cho re-import. */
    ContentTrack saveOrUpdateBySlug(ContentTrack track);

    List<ContentTrack> findAllOrdered();

    Optional<ContentTrack> findBySlug(String slug);
}
