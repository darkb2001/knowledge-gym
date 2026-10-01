package com.knowledgegym.notes.domain.model;

import java.util.UUID;

public record SearchHit(String type, UUID id, String title, String excerpt) {}
