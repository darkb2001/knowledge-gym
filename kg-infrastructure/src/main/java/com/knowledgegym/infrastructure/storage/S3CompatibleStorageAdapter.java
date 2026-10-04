package com.knowledgegym.infrastructure.storage;

import com.knowledgegym.shared.domain.port.StoragePort;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** AWS SDK v2 adapter compatible with Garage's S3 API via endpointOverride. */
@Component
@ConditionalOnProperty(name = "storage.s3.enabled", havingValue = "true")
public final class S3CompatibleStorageAdapter implements StoragePort {
    private final S3Presigner presigner;
    private final S3Client client;
    private final String publicBaseUrl;

    /**
     * @param publicEndpoint hostname mà **browser** gọi được, dùng để ký presigned URL.
     *     Bắt buộc phải khác {@code endpoint} trong Compose: app nói chuyện với Garage qua
     *     DNS nội bộ (`garage:3900`), nhưng URL trả về cho client phải là hostname public
     *     (vd qua Cloudflare Tunnel). Ký bằng `garage:3900` thì browser không resolve được
     *     và mọi upload đều fail — mà lỗi lại nằm ở phía browser nên app không thấy.
     *     Bỏ trống thì fallback về `endpoint` (chỉ đúng khi chạy local cùng máy).
     */
    public S3CompatibleStorageAdapter(
            @Value("${storage.s3.endpoint:http://localhost:3900}") String endpoint,
            @Value("${storage.s3.public-endpoint:}") String publicEndpoint,
            @Value("${storage.s3.region:garage}") String region,
            @Value("${storage.s3.access-key:}") String accessKey,
            @Value("${storage.s3.secret-key:}") String secretKey) {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException("storage.s3.access-key and storage.s3.secret-key are required");
        }
        String signingEndpoint = publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
        this.publicBaseUrl = signingEndpoint.replaceAll("/+$", "");
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(signingEndpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
        // Client server-to-server: PHẢI dùng endpoint nội bộ (garage:3900), không qua Cloudflare.
        this.client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    @Override
    public PresignedUpload presignPut(String bucket, String objectKey, String contentType, Duration lifetime) {
        if (bucket == null || bucket.isBlank() || objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("bucket and objectKey are required");
        }
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException("presigned URL lifetime must be between 1 second and 15 minutes");
        }
        var request = PutObjectRequest.builder().bucket(bucket).key(objectKey)
                .contentType(contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType)
                .build();
        var presigned = presigner.presignPutObject(builder -> builder
                .signatureDuration(lifetime).putObjectRequest(request));
        return new PresignedUpload(bucket, objectKey, presigned.url().toString(),
                publicObjectUrl(bucket, objectKey), lifetime.toSeconds());
    }

    @Override
    public String publicObjectUrl(String bucket, String objectKey) {
        return publicBaseUrl + "/" + bucket + "/" + objectKey;
    }

    @Override
    public void putObject(String bucket, String objectKey, byte[] content, String contentType) {
        if (bucket == null || bucket.isBlank() || objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("bucket and objectKey are required");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("content is required");
        }
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(objectKey)
                        .contentType(contentType == null || contentType.isBlank()
                                ? "application/octet-stream" : contentType)
                        .build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public Optional<byte[]> getObject(String bucket, String objectKey) {
        if (bucket == null || bucket.isBlank() || objectKey == null || objectKey.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket).key(objectKey).build()).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }
}
