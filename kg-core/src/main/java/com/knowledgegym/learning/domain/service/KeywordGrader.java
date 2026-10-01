package com.knowledgegym.learning.domain.service;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** MVP keyword coverage, normalized for Vietnamese accents and whole-word matching. */
public final class KeywordGrader {
    private KeywordGrader() {}

    public record Grade(BigDecimal score, String feedback) {}

    private static final Set<String> STOP = Set.of("the", "and", "for", "with", "this", "that",
            "from", "are", "was", "cua", "cac", "mot", "nhung", "trong", "khi", "cho", "voi",
            "duoc", "khong", "la", "va", "to", "of", "is", "an");

    private static Set<String> tokens(String text) {
        String normalized = Normalizer.normalize(text == null ? "" : text.toLowerCase(Locale.ROOT),
                Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('đ', 'd');
        Set<String> out = new LinkedHashSet<>();
        for (String token : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (token.length() > 2 && !STOP.contains(token)) out.add(token);
        }
        return out;
    }

    public static Grade grade(String sample, String answer) {
        var expected = tokens(sample);
        var actual = tokens(answer);
        var matched = new HashSet<>(expected);
        matched.retainAll(actual);
        BigDecimal score = expected.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(matched.size() * 100L)
                .divide(BigDecimal.valueOf(expected.size()), 2, RoundingMode.HALF_UP);
        var missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        return new Grade(score, missing.isEmpty() ? "Đã đề cập các từ khóa chính."
                : "Từ khóa cần bổ sung: " + String.join(", ", missing.stream().limit(12).toList()));
    }
}
