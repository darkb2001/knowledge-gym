package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.UserRepository;
import com.knowledgegym.shared.domain.port.StoragePort;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ảnh đại diện: upload đi qua server rồi phục vụ lại qua API.
 *
 * <p>Lý do không dùng presigned URL + URL Garage công khai: bucket Garage của homelab không mở
 * public, và host S3 public (`s3.darkb-tech.io.vn`) không có ingress nên trả 522 — `<img>` trong
 * browser không bao giờ tải được. Đi qua API thì ảnh nằm sau đúng domain đã hoạt động.
 *
 * <p>URL công khai: {@code {publicBaseUrl}/users/{userId}/avatar/{objectId}.{ext}}. Object key
 * suy ra được từ URL nên không cần cột DB mới; cả hai đoạn path đều được kiểm tra bằng regex nên
 * không thể thoát ra ngoài prefix {@code avatars/}.
 */
public final class AvatarUseCase {

    /** Ảnh lớn hơn mức này gần như chắc chắn không phải ảnh đại diện; chặn sớm để không tốn RAM. */
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    private static final Map<String, String> CONTENT_TYPES =
            Map.of("png", "image/png", "jpg", "image/jpeg", "webp", "image/webp");

    /** `fileName` phải là `{uuid}.{png|jpg|webp}` — không có `/`, `..` hay path separator nào lọt qua. */
    private static final Pattern FILE_NAME =
            Pattern.compile("^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\.(png|jpg|webp)$");

    private final UserRepository users;
    private final StoragePort storage;
    private final String bucket;
    private final String publicBaseUrl;

    public AvatarUseCase(UserRepository users, StoragePort storage, String bucket, String publicBaseUrl) {
        this.users = users;
        this.storage = storage;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }

    public record AvatarFile(byte[] content, String contentType) {}

    /**
     * Lưu ảnh mới và trỏ `user.avatar_url` sang URL do API phục vụ.
     *
     * @param content bytes thật của ảnh; loại ảnh được nhận diện bằng magic bytes chứ không tin
     *                `Content-Type` client khai báo.
     */
    public User upload(UUID userId, byte[] content) {
        User user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        byte[] bytes = content == null ? new byte[0] : content;
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Ảnh đại diện rỗng");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Ảnh đại diện tối đa 2 MB");
        }
        String extension = detectExtension(bytes)
                .orElseThrow(() -> new IllegalArgumentException("Chỉ nhận ảnh PNG, JPEG hoặc WebP"));
        String objectId = UUID.randomUUID().toString();
        String objectKey = "avatars/" + userId + "/" + objectId + "." + extension;
        storage.putObject(bucket, objectKey, bytes, CONTENT_TYPES.get(extension));
        user.setAvatarUrl(publicBaseUrl + "/users/" + userId + "/avatar/" + objectId + "." + extension);
        return users.save(user);
    }

    /** Đọc ảnh để trả về cho browser; `Optional.empty()` khi không có / tên file không hợp lệ. */
    public Optional<AvatarFile> read(UUID userId, String fileName) {
        if (userId == null || fileName == null) {
            return Optional.empty();
        }
        var matcher = FILE_NAME.matcher(fileName);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String extension = matcher.group(2).toLowerCase(Locale.ROOT);
        String objectKey = "avatars/" + userId + "/" + matcher.group(1) + "." + extension;
        return storage.getObject(bucket, objectKey)
                .map(bytes -> new AvatarFile(bytes, CONTENT_TYPES.get(extension)));
    }

    /** Nhận diện định dạng bằng magic bytes: PNG `89 50 4E 47`, JPEG `FF D8 FF`, WebP `RIFF….WEBP`. */
    private static Optional<String> detectExtension(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return Optional.of("png");
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return Optional.of("jpg");
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return Optional.of("webp");
        }
        return Optional.empty();
    }
}
