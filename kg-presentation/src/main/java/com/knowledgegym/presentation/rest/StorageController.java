package com.knowledgegym.presentation.rest;

import com.knowledgegym.shared.domain.port.StoragePort;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/storage")
// `@ConditionalOnBean(StoragePort.class)` phụ thuộc thứ tự đăng ký bean: controller
// quét trước adapter thì điều kiện sai và endpoint biến mất im lặng. Gate theo
// cùng property mà adapter dùng — kết quả xác định, không phụ thuộc thứ tự.
@ConditionalOnProperty(name = "storage.s3.enabled", havingValue = "true")
public class StorageController {
    private static final Set<String> ALLOWED_BUCKETS = Set.of("kg-blog-images", "kg-avatars", "kg-exports");
    private final StoragePort storage;

    public StorageController(StoragePort storage) {
        this.storage = storage;
    }

    @PostMapping("/presigned-put")
    public ResponseEntity<?> presignedPut(@AuthenticationPrincipal UUID userId,
                                          @RequestParam String bucket,
                                          @RequestParam String filename,
                                          @RequestParam(defaultValue = "application/octet-stream") String contentType) {
        if (!ALLOWED_BUCKETS.contains(bucket) || filename.isBlank() || filename.length() > 120
                || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return ResponseEntity.badRequest().body("Invalid storage bucket or filename");
        }
        String key = userId + "/" + UUID.randomUUID() + "-" + filename;
        return ResponseEntity.status(HttpStatus.OK)
                .body(storage.presignPut(bucket, key, contentType, Duration.ofMinutes(10)));
    }
}
