package com.knowledgegym.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * `app.content.*` — **nguồn duy nhất** cho cấu hình content import.
 *
 * Trước đây `docs-path` và `parse-parallelism` được đọc bằng `@Value` ở 2 class khác nhau
 * trong khi record này không ai inject → hai đường bind song song, default khác nhau, dễ lệch.
 * Giờ chỉ record này được inject.
 *
 * Về {@code docsPath}: `docs/` nằm ngoài git root của repo (`knowledge-gym/`), nên **không có
 * một relative path nào đúng cho mọi ngữ cảnh** (working dir lúc `bootRun` là `kg-presentation/`,
 * trong container là `/app`). Vì vậy đi theo thứ tự: giá trị cấu hình → các ứng viên tương đối,
 * và chỉ nhận ứng viên nào **thật sự là thư mục docs** (có `index.html` + file `NN-slug.html`).
 * Kiểm tra nội dung quan trọng hơn `Files.isDirectory`: `knowledge-gym/docs/` cũng là thư mục
 * nhưng chỉ chứa markdown kế hoạch.
 */
@Configuration
@EnableConfigurationProperties(AppContentProperties.Content.class)
public class AppContentProperties {

    @ConfigurationProperties(prefix = "app.content")
    public record Content(String docsPath, Integer parseParallelism) {

        /** Ứng viên cuối: đi lên 2 cấp từ `kg-presentation/` → `javaNote/docs`. */
        static final List<String> RELATIVE_CANDIDATES = List.of("../../docs", "../docs", "docs");

        public Content {
            if (parseParallelism == null || parseParallelism < 1) {
                parseParallelism = 4;
            }
            if (docsPath == null || docsPath.isBlank()) {
                docsPath = RELATIVE_CANDIDATES.get(0);
            }
        }

        /**
         * Thư mục docs dùng được đầu tiên: giá trị cấu hình trước, rồi các ứng viên tương đối.
         *
         * @return path đã resolve; nếu không có ứng viên nào hợp lệ thì trả path cấu hình ban đầu
         *         để caller báo lỗi kèm ngữ cảnh (không ném ở đây — exception thuộc kg-core).
         */
        public Path resolveDocsPath() {
            Path configured = Path.of(docsPath).toAbsolutePath().normalize();
            if (isDocsDirectory(configured)) {
                return configured;
            }
            for (String candidate : RELATIVE_CANDIDATES) {
                Path path = Path.of(candidate).toAbsolutePath().normalize();
                if (isDocsDirectory(path)) {
                    return path;
                }
            }
            return configured;
        }

        /** docs/ hợp lệ = có `index.html` (seed topics/modules) và ít nhất 1 file module. */
        private static boolean isDocsDirectory(Path path) {
            if (!Files.isDirectory(path) || !Files.isRegularFile(path.resolve("index.html"))) {
                return false;
            }
            try (var files = Files.list(path)) {
                return files.anyMatch(file -> file.getFileName().toString().matches("\\d{2}-[a-z0-9-]+\\.html"));
            } catch (java.io.IOException e) {
                return false;
            }
        }
    }
}
