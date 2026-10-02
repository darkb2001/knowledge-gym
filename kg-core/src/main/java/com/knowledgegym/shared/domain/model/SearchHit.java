package com.knowledgegym.shared.domain.model;

import java.util.UUID;

/**
 * One global-search result across questions, the caller's notes and published blog posts.
 *
 * Lives in `shared` rather than under `notes` because the global search corpus spans three
 * modules. It was previously owned by the notes domain, which meant the notes module
 * appeared to define what a question or blog hit looks like.
 */
public record SearchHit(String type, UUID id, String title, String excerpt) {}
