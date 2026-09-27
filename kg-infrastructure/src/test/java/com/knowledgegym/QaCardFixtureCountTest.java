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
 * Log kết quả thực (không assume 240). Fixtures HTML sẽ được thêm ở m3/m4 khi parse content.
 */
class QaCardFixtureCountTest {

    private static final Logger log = LoggerFactory.getLogger(QaCardFixtureCountTest.class);

    @Test
    void countQaCardsInDocs() throws Exception {
        Path docsDir = resolveDocsDir();
        if (docsDir == null) {
            log.warn("docs/ not found — skip fixture count (HTML .qa-card arrives with content parser m4)");
            return;
        }

        int totalCount = 0;
        int htmlFiles = 0;
        try (Stream<Path> paths = Files.list(docsDir)) {
            Path[] files = paths.filter(p -> p.toString().endsWith(".html")).toArray(Path[]::new);
            htmlFiles = files.length;
            log.info("Found {} HTML files in {}", htmlFiles, docsDir.toAbsolutePath());

            for (Path htmlFile : files) {
                Document doc = Jsoup.parse(htmlFile.toFile(), "UTF-8");
                int count = doc.select(".qa-card").size();
                log.info("  {} → {} .qa-card", htmlFile.getFileName(), count);
                totalCount += count;
            }
        }

        log.info("TOTAL .qa-card count across docs/*.html: {} (from {} files)", totalCount, htmlFiles);
        // Informational until content fixtures land (m4). Do not assert > 0 yet.
    }

    /** Resolve docs/ from common working dirs (module root, project root, CI checkout). */
    private static Path resolveDocsDir() {
        Path[] candidates = {
                Paths.get("docs"),
                Paths.get("../docs"),
                Paths.get("../../docs"),
                Paths.get("knowledge-gym/docs")
        };
        for (Path candidate : candidates) {
            Path normalized = candidate.normalize();
            if (Files.isDirectory(normalized)) {
                return normalized;
            }
        }
        return null;
    }
}