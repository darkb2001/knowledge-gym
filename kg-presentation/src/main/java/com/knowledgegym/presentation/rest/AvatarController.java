package com.knowledgegym.presentation.rest;

import com.knowledgegym.identity.application.AvatarUseCase;
import com.knowledgegym.progress.application.QueryProgressUseCase;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload + phục vụ ảnh đại diện qua API thay vì presigned URL trực tiếp tới Garage.
 *
 * <p>Presigned PUT chỉ hoạt động khi browser truy cập được host S3; ở homelab host đó không có
 * ingress (522) nên upload fail im lặng và `<img>` cũng không tải được. Đi qua API thì ảnh dùng
 * đúng domain đang chạy, không cần mở bucket public.
 *
 * <p>Gate theo cùng property với {@code S3CompatibleStorageAdapter} — xem ghi chú ở
 * {@link StorageController}.
 */
@RestController
@ConditionalOnProperty(name = "storage.s3.enabled", havingValue = "true")
public class AvatarController {

    /** Chỉ 3 loại ảnh {@link AvatarUseCase} nhận; khai báo ở đây để lỗi trả về sớm và rõ ràng. */
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/png", "image/jpeg", "image/jpg", "image/webp");

    private final AvatarUseCase avatars;
    private final QueryProgressUseCase progress;

    public AvatarController(AvatarUseCase avatars, QueryProgressUseCase progress) {
        this.avatars = avatars;
        this.progress = progress;
    }

    @PostMapping(value = "/users/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserController.ProfileResponse upload(@AuthenticationPrincipal UUID userId,
                                                 @RequestPart("file") MultipartFile file) throws IOException {
        String declared = file.getContentType();
        if (declared != null && !ALLOWED_CONTENT_TYPES.contains(declared.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Chỉ nhận ảnh PNG, JPEG hoặc WebP");
        }
        var updated = avatars.upload(userId, file.getBytes());
        return UserController.ProfileResponse.of(updated, progress.stats(userId));
    }

    /**
     * Public (không cần token) vì browser tải ảnh bằng thẻ `<img>`. Tên file là `{uuid}.{ext}` nên
     * không thể trỏ ra ngoài prefix `avatars/{userId}/`; URL có `?v=` đổi sau mỗi lần upload.
     */
    @GetMapping("/users/{userId}/avatar/{fileName}")
    public ResponseEntity<byte[]> avatar(@PathVariable UUID userId, @PathVariable String fileName) {
        return avatars.read(userId, fileName)
                .map(file -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(file.contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                        .body(file.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
