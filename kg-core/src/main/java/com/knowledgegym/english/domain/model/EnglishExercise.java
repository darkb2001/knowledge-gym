package com.knowledgegym.english.domain.model;

import java.util.List;

/** Versioned, original mini-practice material, not an official VSTEP exam. */
public record EnglishExercise(String id, Skill skill, String title, String focus, int minutes,
                              int minimumWords, String prompt, String passage, String audioPath,
                              String transcript, List<Item> items, List<String> checklist) {
    public enum Skill { LISTENING, SPEAKING, READING, WRITING }
    public record Item(String id, String stem, List<String> options, int correctIndex, String explanation) {}
}
