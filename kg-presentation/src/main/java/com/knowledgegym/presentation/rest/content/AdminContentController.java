package com.knowledgegym.presentation.rest.content;

import com.knowledgegym.content.application.ManageQuestionsUseCase;
import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.port.ContentImportJob;
import com.knowledgegym.presentation.rest.content.dto.AdminQuestionDTO;
import com.knowledgegym.shared.application.NotFoundException;
import com.knowledgegym.shared.domain.model.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Endpoint quản trị nội dung. Toàn bộ class yêu cầu role ADMIN — USER gọi vào phải nhận 403
 * (đóng deferred RBAC từ m3, xem `docs/14-m3-journal.md`).
 *
 * Mutation đồng bộ evict **cả 3 cache** (`questions`, `topics`, `modules`): `topics`/`modules`
 * chứa `moduleCount`/`questionCount` phái sinh, nên thêm/xoá 1 câu hỏi làm số đếm trong 2 cache
 * kia sai ngay.
 *
 * `parse()` là async (202 rồi mới chạy nền) nên **không** khai `@CacheEvict` ở đây — evict ngay
 * lúc trả response sẽ chỉ xoá cache trước khi dữ liệu mới được ghi. Job adapter evict sau khi
 * import xong, xem {@link ContentImportJob}.
 */
@RestController
@RequestMapping("/admin/content")
@PreAuthorize("hasRole('ADMIN')")
public class AdminContentController {

    private final ContentImportJob importJob;
    private final ManageQuestionsUseCase manageQuestionsUseCase;

    public AdminContentController(ContentImportJob importJob,
                                  ManageQuestionsUseCase manageQuestionsUseCase) {
        this.importJob = importJob;
        this.manageQuestionsUseCase = manageQuestionsUseCase;
    }

    public record ImportSubmission(UUID jobId, String status) {}

    public record ImportJobResponse(UUID jobId,
                                    String status,
                                    Instant startedAt,
                                    Instant finishedAt,
                                    ContentImportJob.ImportStats result,
                                    String errorMessage) {
        static ImportJobResponse from(ContentImportJob.JobSnapshot snap) {
            return new ImportJobResponse(snap.id(), snap.status().name(), snap.startedAt(),
                    snap.finishedAt(), snap.result(), snap.errorMessage());
        }
    }

    /** Trả 202 ngay: import 15 file HTML có thể vượt timeout proxy. */
    @PostMapping("/parse")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ImportSubmission parse() {
        var job = importJob.submit();
        return new ImportSubmission(job.id(), job.status().name());
    }

    /**
     * Đọc trạng thái job import — đóng gap: 202 trả {@code jobId} nhưng trước đây không có
     * endpoint theo dõi SUCCEEDED/FAILED (import thất bại vô hình với caller).
     */
    @GetMapping("/jobs/{jobId}")
    public ImportJobResponse job(@PathVariable UUID jobId) {
        return importJob.find(jobId)
                .map(ImportJobResponse::from)
                .orElseThrow(() -> new NotFoundException("Import job không tồn tại: " + jobId));
    }

    public record QuestionCreateRequest(@NotNull UUID moduleId,
                                        @NotBlank String title,
                                        @NotBlank String answerHtml,
                                        String difficulty,
                                        List<String> tags,
                                        Integer sortOrder) {}

    public record QuestionUpdateRequest(String title,
                                        String answerHtml,
                                        String difficulty,
                                        List<String> tags) {}

    @PostMapping("/questions")
    @ResponseStatus(HttpStatus.CREATED)
    @CacheEvict(value = {"questions", "topics", "modules"}, allEntries = true)
    public AdminQuestionDTO create(@Valid @RequestBody QuestionCreateRequest request) {
        Question created = manageQuestionsUseCase.create(new ManageQuestionsUseCase.CreateCommand(
                request.moduleId(), request.title(), request.answerHtml(),
                parseDifficulty(request.difficulty()), request.tags(), request.sortOrder()));
        return AdminQuestionDTO.from(created, List.of());
    }

    /** Override độ khó/chỉnh sửa thủ công — ghi đè difficulty mặc định từ import. */
    @PatchMapping("/questions/{id}")
    @CacheEvict(value = {"questions", "topics", "modules"}, allEntries = true)
    public AdminQuestionDTO update(@PathVariable UUID id,
                                   @Valid @RequestBody QuestionUpdateRequest request) {
        Question updated = manageQuestionsUseCase.update(id, new ManageQuestionsUseCase.UpdateCommand(
                request.title(), request.answerHtml(), parseDifficulty(request.difficulty()), request.tags()));
        return AdminQuestionDTO.from(updated, List.of());
    }

    @DeleteMapping("/questions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @CacheEvict(value = {"questions", "topics", "modules"}, allEntries = true)
    public void delete(@PathVariable UUID id) {
        manageQuestionsUseCase.delete(id);
    }

    private static Difficulty parseDifficulty(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Difficulty.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("difficulty không hợp lệ: " + raw);
        }
    }
}
