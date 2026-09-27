package com.knowledgegym.presentation.rest.auth;

import com.knowledgegym.identity.application.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** RFC 7807 Problem Details — chuẩn lỗi REST cho toàn bộ API. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, Object>> handleAuthException(AuthException ex) {
        HttpStatus status = switch (ex.getKind()) {
            case CONFLICT -> HttpStatus.CONFLICT;
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
        };
        return problem(status, "auth_error", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        // Không leak raw message nếu có chứa email — generic detail
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
        return problem(HttpStatus.BAD_REQUEST, "validation_error", detail);
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