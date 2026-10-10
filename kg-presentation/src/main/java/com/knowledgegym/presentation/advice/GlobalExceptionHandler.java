package com.knowledgegym.presentation.advice;

import com.knowledgegym.content.application.ContentImportException;
import com.knowledgegym.identity.application.AuthException;
import com.knowledgegym.shared.application.ConflictException;
import com.knowledgegym.shared.application.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.knowledgegym.search.application.ElasticsearchLifecycleUseCase;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** RFC 7807 Problem Details — chuẩn lỗi REST cho toàn bộ API. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, Object>> handleAuthException(AuthException ex) {
        HttpStatus status = switch (ex.getKind()) {
            case CONFLICT -> HttpStatus.CONFLICT;
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
        };
        return problem(status, "auth_error", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        // Register TOCTOU race trên uk_users_email → 409 generic (không leak email)
        return problem(HttpStatus.CONFLICT, "conflict", "Resource already exists");
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not_found", ex.getMessage());
    }

    /**
     * Vi phạm ràng buộc duy nhất nhưng tài nguyên đích vẫn tồn tại (vd `sortOrder` đã dùng).
     * 409 để client phân biệt được với 400 (input sai định dạng) và 404 (không tìm thấy).
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "conflict", ex.getMessage());
    }

    /**
     * Lỗi import nội dung (thiếu index.html, path sai, module trỏ topic lạ) là lỗi cấu hình
     * vận hành, không phải lỗi client → 500. Message trả về là **hằng số**, không dùng
     * `ex.getMessage()` vì nó chứa đường dẫn tuyệt đối của host (leak cấu trúc thư mục).
     * Chi tiết đầy đủ nằm trong log của use case/parser.
     */
    @ExceptionHandler(ContentImportException.class)
    public ResponseEntity<Map<String, Object>> handleContentImport(ContentImportException ex) {
        log.error("Content import thất bại", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "content_import_failed",
                "Import nội dung thất bại. Kiểm tra log server để biết chi tiết.");
    }

    /**
     * `@PreAuthorize` từ chối → 403 Problem Details thay vì body HTML mặc định.
     * Không dùng message gốc vì nó thường rỗng/lộ chi tiết nội bộ.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Bạn không có quyền thực hiện thao tác này");
    }

    /**
     * Path không khớp handler nào. Không có handler này thì lỗi rơi ra ngoài thành 500/401 khó hiểu
     * — với API thì 404 JSON rõ nghĩa hơn.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not_found", "Endpoint không tồn tại");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "payload_too_large", "File vượt quá kích thước cho phép");
    }

    @ExceptionHandler(ElasticsearchLifecycleUseCase.HostOperationException.class)
    public ResponseEntity<Map<String, Object>> handleHostOperation(
            ElasticsearchLifecycleUseCase.HostOperationException ex) {
        HostScriptResultView result = HostScriptResultView.from(ex.result(), objectMapper);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("exitCode", result.exitCode());
        body.put("output", result.output());
        body.put("settings", null);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.warn("Infrastructure operation failed", ex);
        return problem(HttpStatus.BAD_GATEWAY, "bad_gateway", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        String detail = ex.getMessage() != null && ex.getMessage().contains("@")
                ? "Invalid request"
                : ex.getMessage();
        return problem(HttpStatus.BAD_REQUEST, "bad_request", detail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        var response = problem(HttpStatus.BAD_REQUEST, "validation_error", detail);
        var body = new LinkedHashMap<>(response.getBody());
        body.put("fieldErrors", ex.getBindingResult().getFieldErrors().stream()
                .collect(java.util.stream.Collectors.toMap(
                        org.springframework.validation.FieldError::getField,
                        error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
                        (first, second) -> first, LinkedHashMap::new)));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private record HostScriptResultView(int exitCode, Object output) {
        static HostScriptResultView from(
                com.knowledgegym.shared.domain.port.HostScriptPort.Result result,
                ObjectMapper objectMapper) {
            Object output = result.output();
            if (result.output() != null && !result.output().isBlank()) {
                try {
                    JsonNode json = objectMapper.readTree(result.output());
                    output = json;
                } catch (RuntimeException ignored) {
                    // Keep non-JSON helper output as text.
                }
            }
            return new HostScriptResultView(result.exitCode(), output);
        }
    }

    private ResponseEntity<Map<String, Object>> problem(HttpStatus status, String code, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", code);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
