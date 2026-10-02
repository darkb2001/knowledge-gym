package com.knowledgegym.shared.domain.port;

import java.time.Duration;

/** S3-compatible object storage boundary; implementations may target Garage or AWS S3. */
public interface StoragePort {
    PresignedUpload presignPut(String bucket, String objectKey, String contentType, Duration lifetime);

    record PresignedUpload(String bucket, String objectKey, String url, long expiresInSeconds) {}
}
