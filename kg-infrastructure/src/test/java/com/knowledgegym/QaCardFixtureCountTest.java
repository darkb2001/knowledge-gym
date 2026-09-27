package com.knowledgegym;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Parser fixture count — đếm số .qa-card trong docs/*.html.
 * Log kết quả thực (không assume 240). Mục đích: tracking nội dung qua mini-phases.
 *
 * Chạy trong kg-infrastructure module vì module này có jsoup dependency.
 */
class QaCardFixtureCountTest {

    private static final Logger log = LoggerFactory.getLogger(QaCardFixtureCountTest.class);

    @Test
    void countQaCardsInDocs() throws Exception {
        Path docsDir = Paths.get("../../docs").normalize();

        if (!Files.exists(docsDir)) {
            log.warn("docs directory not found at {} — skipping count (expected in non-gradle contexts)", docsDir);
            return;
        }

        int totalCount = 0;
        try (Stream<Path> paths = Files.list(docsDir)) {
            Path[] htmlFiles = paths.filter(p -> p.toString().endsWith(".html")).toArray(Path[]::new);
            log.info("Found {} HTML files in docs/", htmlFiles.length);

            for (Path htmlFile : htmlFiles) {
                Document doc = Jsoup.parse(htmlFile.toFile(), "UTF-8");
                int count = doc.select(".qa-card").size();
                log.info("  {} → {} .qa-card", htmlFile.getFileName(), count);
                totalCount += count;
            }
        }

        log.info("TOTAL .qa-card count across docs/*.html: {}", totalCount);
        // Plan criterion: "Số câu thật được logged (không assume 240)" — count is informational.
        // When .qa-card fixtures are added in m3+, assertTrue(totalCount > 0) will be the gate.
    }
}