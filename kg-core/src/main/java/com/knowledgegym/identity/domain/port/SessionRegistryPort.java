package com.knowledgegym.identity.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Danh sách phiên đăng nhập đang sống của một user.
 *
 * Mỗi phiên = một refresh-token family = một thiết bị/trình duyệt (xem LoginUseCase: mỗi lần
 * đăng nhập sinh familyId mới). Đọc từ bảng audit refresh_tokens — cùng nguồn với cơ chế revoke
 * nên danh sách không thể lệch trạng thái so với thứ thực sự còn dùng được.
 */
public interface SessionRegistryPort {

    record SessionSummary(UUID familyId, Instant createdAt, Instant lastSeenAt,
                          String ipAddress, String userAgent) {}

    /** Family còn hiệu lực (chưa revoke, chưa hết hạn), dùng gần đây nhất lên đầu. */
    List<SessionSummary> listActive(UUID userId);
}
