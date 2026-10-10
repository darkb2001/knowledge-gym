package com.knowledgegym.english.domain.model;

import java.util.List;

/** Presentation grouping only; every answer remains bound to the exercise's immutable item ID. */
public record EnglishExercisePart(String id, String title, String passage, String audioPath, List<String> itemIds) {
    public EnglishExercisePart { itemIds = List.copyOf(itemIds); }
}
