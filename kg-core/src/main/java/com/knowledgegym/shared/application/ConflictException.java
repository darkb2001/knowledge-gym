package com.knowledgegym.shared.application;

/**
 * Xung đột dữ liệu → presentation map 409 (RFC 7807 `conflict`).
 *
 * Khác {@link NotFoundException} (404): tài nguyên đích tồn tại, nhưng thao tác vi phạm
 * ràng buộc duy nhất. Ví dụ: admin tạo question với `sortOrder` đã bị chiếm trong module —
 * nếu im lặng upsert thì câu cũ bị ghi đè và client nhận 201 như thành công.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
