package com.knowledgegym.blog.domain.service;

import java.util.Locale;

/** Heuristic editorial score. It measures format and grounding signals, never factual truth. */
public final class QualityScorer {
    public int score(String title, String body, String excerpt, int validatedSourceCount) {
        String text = body == null ? "" : body;
        String lower = text.toLowerCase(Locale.ROOT);
        int result = 0;
        if (title != null && title.length() >= 20 && title.length() <= 100) result += 15;
        if (text.length() >= 1_500) result += 25;
        else if (text.length() >= 700) result += 18;
        else if (text.length() >= 350) result += 10;
        int headings = count(lower, "<h2");
        result += Math.min(20, headings * 5);
        if (lower.contains("<pre") || lower.contains("<code")) result += 10;
        if (excerpt != null && excerpt.length() >= 40 && excerpt.length() <= 300) result += 10;
        result += Math.min(20, validatedSourceCount * 10);
        return Math.min(100, result);
    }
    private static int count(String text, String needle) {
        int result = 0, from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) { result++; from += needle.length(); }
        return result;
    }
}
