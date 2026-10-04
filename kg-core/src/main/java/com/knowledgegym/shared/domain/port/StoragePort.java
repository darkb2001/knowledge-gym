package com.knowledgegym.shared.domain.port;

import java.time.Duration;

/** S3-compatible object storage boundary; implementations may target Garage or AWS S3. */
public interface StoragePort {
    PresignedUpload presignPut(String bucket, String objectKey, String contentType, Duration lifetime);

    /** Public URL clients use after a successful presigned PUT (path-style S3). */
    String publicObjectUrl(String bucket, String objectKey);

    /**
     * Upload do server thực hiện (không qua browser) — dùng cho avatar vì bucket Garage
     * không mở public: client không thể PUT trực tiếp, cũng không đọc được object.
     */
    void putObject(String bucket, String objectKey, byte[] content, String contentType);

    /** Đọc object để phục vụ lại qua API; `Optional.empty()` khi object không tồn tại. */
    java.util.Optional<byte[]> getObject(String bucket, String objectKey);

    record PresignedUpload(String bucket, String objectKey, String url, String publicUrl,
                           long expiresInSeconds) {}
}
