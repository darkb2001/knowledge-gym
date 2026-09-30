package com.knowledgegym.infrastructure.content;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentImportJobServiceTest {

    @Test
    void sanitizeErrorMessageReplacesDocsPath() {
        String docs = "/Users/dev/project/docs";
        RuntimeException ex = new RuntimeException("Không đọc được " + docs + "/01-java-core.html");

        String safe = ContentImportJobService.sanitizeErrorMessage(ex, docs);

        assertThat(safe).doesNotContain("/Users/dev");
        assertThat(safe).contains("<docs>/01-java-core.html");
    }

    @Test
    void sanitizeErrorMessageFallsBackToExceptionNameWhenMessageBlank() {
        RuntimeException ex = new RuntimeException("   ");
        assertThat(ContentImportJobService.sanitizeErrorMessage(ex, "/tmp/docs"))
                .isEqualTo("RuntimeException");
    }
}
