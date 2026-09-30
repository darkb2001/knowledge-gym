package com.knowledgegym.content.application;

import com.knowledgegym.shared.domain.model.Difficulty;

import java.util.Map;

/**
 * Difficulty mặc định theo module — HTML docs/ KHÔNG có marker junior/mid/senior
 * (đã kiểm: không có `data-difficulty`, không có badge level), nên phải suy từ module.
 *
 * Đây là **phán đoán khởi tạo**, không phải ground truth: admin override qua
 * `PATCH /questions/{id}` và bản ghi trong DB là nguồn sự thật sau lần import đầu.
 * Chỉ ảnh hưởng **câu mới** ở lần import kế tiếp (upsert không ghi đè difficulty đã sửa tay).
 */
public final class ModuleDifficultyDefaults {

    private static final Map<String, Difficulty> OVERRIDES = Map.of(
            "10", Difficulty.JUNIOR,   // Daifuku WMS/WCS — domain notes, không phải core interview
            "11", Difficulty.JUNIOR,   // Coding & DSA — nền tảng
            "14", Difficulty.JUNIOR,   // Kế hoạch ôn tập
            "07", Difficulty.SENIOR,   // Microservices & Distributed
            "08", Difficulty.SENIOR,   // System Design & Scalability
            "09", Difficulty.SENIOR,   // Cloud, CI/CD & Testing
            "12", Difficulty.SENIOR,   // Software Design & UML
            "13", Difficulty.SENIOR    // Design Patterns
    );

    private static final Difficulty DEFAULT = Difficulty.MID;

    private ModuleDifficultyDefaults() {
    }

    /** @param moduleSlug slug dạng `05-database` / `01-java-core` (số ở đầu). */
    public static Difficulty forModuleSlug(String moduleSlug) {
        if (moduleSlug == null || moduleSlug.length() < 2) {
            return DEFAULT;
        }
        String prefix = moduleSlug.substring(0, 2);
        return OVERRIDES.getOrDefault(prefix, DEFAULT);
    }
}
