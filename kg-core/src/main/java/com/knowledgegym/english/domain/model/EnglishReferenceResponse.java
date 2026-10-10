package com.knowledgegym.english.domain.model;

import java.util.List;

/** Original worked example, not an official answer key or a certified band score. */
public record EnglishReferenceResponse(String title, String text, List<Note> notes) {
    public record Note(String vi, String en) {}
    public EnglishReferenceResponse { notes = List.copyOf(notes); }
}
