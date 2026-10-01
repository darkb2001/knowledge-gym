package com.knowledgegym.progress.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MasteryCalculator {
    private MasteryCalculator() {}

    public static BigDecimal percentage(int correct, int total) {
        if (total <= 0) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(correct).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
