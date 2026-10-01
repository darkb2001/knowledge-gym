package com.knowledgegym.learning.domain.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class KeywordGraderTest {
 @Test void normalizesAccentsCaseAndWholeWords(){assertThat(KeywordGrader.grade("Đồng bộ thread lock", "dong bo THREAD lock").score()).isEqualByComparingTo("100");assertThat(KeywordGrader.grade("lock", "deadlock").score()).isEqualByComparingTo("0");}
 @Test void blankAnswersScoreZeroAndExposeMissingKeywords(){var result=KeywordGrader.grade("thread safety", "");assertThat(result.score()).isEqualByComparingTo("0");assertThat(result.feedback()).contains("thread","safety");}
}
