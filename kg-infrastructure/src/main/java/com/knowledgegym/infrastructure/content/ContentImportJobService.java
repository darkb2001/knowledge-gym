package com.knowledgegym.infrastructure.content;

import com.knowledgegym.content.application.ImportContentUseCase;
import com.knowledgegym.content.domain.port.ContentImportJob;
import com.knowledgegym.infrastructure.config.AppContentProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Chạy import docs/ nền và trả job id ngay (HTTP 202).
 *
 * Lý do async: 15 file HTML + 263 upsert có thể vượt timeout của reverse proxy (nginx
 * `proxy_read_timeout`), và lỗi giữa chừng cần đọc được trạng thái thay vì mất theo response.
 *
 * Dùng `TaskExecutor` inject sẵn thay vì `@Async`: `@Async` không hoạt động khi gọi nội bộ
 * (self-invocation không qua proxy) — đây là lỗi im lặng, job sẽ chạy đồng bộ đúng lúc
 * mình tưởng nó đang nền.
 *
 * {@link ImportContentUseCase#execute} chạy trong {@link TransactionTemplate}: một lần import
 * topics→modules→questions要么 toàn bộ commit,要么 rollback — tránh catalog nửa vời khi fail
 * giữa chừng (gap đã ghi trong journal m4).
 *
 * Registry in-memory: đủ cho thao tác admin thủ công, chạy 1 replica. Nhiều replica → cần Redis/PG
 * (để dành khi thật sự scale, xem `plans/knowledge-gym/plan.md` phần deferred).
 */
@Service
public class ContentImportJobService implements ContentImportJob {

    /** Giữ tối đa 20 job gần nhất để không phình memory ở process sống lâu. */
    private static final int MAX_JOBS = 20;

    /** Cache phái sinh từ nội dung import — phải xoá cùng nhau sau khi ghi. */
    private static final List<String> CONTENT_CACHES = List.of("questions", "topics", "modules");

    private final ImportContentUseCase importContentUseCase;
    private final TaskExecutor taskExecutor;
    private final AppContentProperties.Content properties;
    private final CacheManager cacheManager;
    private final TransactionTemplate transactionTemplate;
    private final ConcurrentMap<UUID, JobSnapshot> jobs = new ConcurrentHashMap<>();

    public ContentImportJobService(ImportContentUseCase importContentUseCase,
                                   @Qualifier("contentImportExecutor") TaskExecutor taskExecutor,
                                   AppContentProperties.Content properties,
                                   CacheManager cacheManager,
                                   TransactionTemplate transactionTemplate) {
        this.importContentUseCase = importContentUseCase;
        this.taskExecutor = taskExecutor;
        this.properties = properties;
        this.cacheManager = cacheManager;
        this.transactionTemplate = transactionTemplate;
    }

    /** @return trạng thái ngay sau khi submit (RUNNING) — controller trả 202 + id. */
    @Override
    public JobSnapshot submit() {
        UUID jobId = UUID.randomUUID();
        JobSnapshot submitted = new JobSnapshot(jobId, Status.RUNNING, Instant.now(), null, null, null);
        jobs.put(jobId, submitted);
        evictOldJobsIfNeeded(jobId);
        taskExecutor.execute(() -> run(jobId));
        // Trả object đã tạo, không đọc lại map: worker có thể đã chạy xong và ghi đè entry
        // thành SUCCEEDED trước khi ta kịp đọc, làm response 202 báo sai trạng thái.
        return submitted;
    }

    private void run(UUID jobId) {
        JobSnapshot submitted = jobs.get(jobId);
        if (submitted == null) {
            // Entry bị evict bởi evictOldJobsIfNeeded khi submit dồn dập — không còn gì để báo cáo.
            return;
        }
        Instant startedAt = submitted.startedAt();
        String docsPath = null;
        try {
            // Resolve ở đây (không phải lúc khởi động): path phụ thuộc working dir của process,
            // và ta muốn request nhận lỗi rõ ràng thay vì app fail to start.
            docsPath = properties.resolveDocsPath().toString();
            String path = docsPath;
            ImportContentUseCase.ImportResult result = java.util.Objects.requireNonNull(
                    transactionTemplate.execute(status -> importContentUseCase.execute(path)),
                    "ImportContentUseCase returned null");
            jobs.put(jobId, new JobSnapshot(jobId, Status.SUCCEEDED, startedAt, Instant.now(),
                    toStats(result), null));
        } catch (RuntimeException e) {
            jobs.put(jobId, new JobSnapshot(jobId, Status.FAILED, startedAt, Instant.now(), null,
                    sanitizeErrorMessage(e, docsPath)));
        } finally {
            // Evict luôn: SUCCEEDED cần xoá count cũ; FAILED sau rollback vẫn an toàn (clear no-op
            // về mặt dữ liệu) và tránh hit stale nếu một phần code path nào đó đã ghi ngoài TX.
            evictContentCaches();
        }
    }

    private static ImportStats toStats(ImportContentUseCase.ImportResult result) {
        return new ImportStats(result.topics(), result.modules(), result.questions(),
                result.deleted(), result.orphanModuleSlugs());
    }

    /**
     * Không lộ absolute filesystem path qua job registry (kể cả khi sau này có GET jobs).
     * Thay docs path bằng {@code <docs>}; message null → tên exception.
     */
    static String sanitizeErrorMessage(RuntimeException e, String docsPath) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            return e.getClass().getSimpleName();
        }
        if (docsPath != null && !docsPath.isBlank()) {
            msg = msg.replace(docsPath, "<docs>");
            try {
                String absolute = Path.of(docsPath).toAbsolutePath().normalize().toString();
                msg = msg.replace(absolute, "<docs>");
            } catch (RuntimeException ignored) {
                // Path.of có thể fail với chuỗi lạ — giữ message đã replace tương đối.
            }
        }
        return msg;
    }

    /**
     * Evict thủ công thay vì `@CacheEvict`: annotation chỉ hoạt động trên lời gọi qua proxy,
     * còn đây là thân của task chạy nền (`taskExecutor.execute(() -> run(jobId))`) — self-invocation
     * nên proxy không cắt ngang và annotation sẽ im lặng không làm gì.
     */
    private void evictContentCaches() {
        for (String name : CONTENT_CACHES) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    @Override
    public Optional<JobSnapshot> find(UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    /**
     * Giữ map ở mức {@link #MAX_JOBS}. Loại nhiều entry một lượt thay vì 1 entry/lần submit:
     * submit dồn dập có thể vượt trần trước khi lần dọn kế tiếp chạy.
     */
    private void evictOldJobsIfNeeded(UUID keep) {
        while (jobs.size() > MAX_JOBS) {
            Optional<UUID> oldest = jobs.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(keep))
                    .min(Comparator.comparing(entry -> entry.getValue().startedAt()))
                    .map(Map.Entry::getKey);
            if (oldest.isEmpty()) {
                return;
            }
            jobs.remove(oldest.get());
        }
    }
}
