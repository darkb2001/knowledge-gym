package com.knowledgegym.shared.domain.port;

import java.time.Duration;

/**
 * Distributed lock — dùng cho debounce (chặn 2 request trùng trong vài giây), **không** phải guard
 * tính đúng đắn.
 *
 * <p>Hợp đồng quan trọng: implementation phải **fail-open**. Redis chết không được làm submit quiz
 * thất bại — tính đúng đắn (không double-submit) do unique constraint ở DB giữ
 * (`uk_quiz_answers_session_question`, V016). Khoá này chỉ để giảm tải và tránh log rác.
 *
 * <p>Port thuần Java: domain/application không biết Redis.
 */
public interface DistributedLockPort {

    /**
     * Thử giành lock.
     *
     * @param key   khoá, vd `lock:quiz:{sessionId}:{userId}`
     * @param token định danh **của caller** (UUID mỗi lần thử). Cần để release đúng lock mình giữ:
     *              nếu TTL hết hạn và request khác đã giành lock, xoá theo key sẽ xoá lock của
     *              người khác và mở cửa cho 2 request chạy song song.
     * @return `true` nếu được tiếp tục (giành được lock, **hoặc** Redis không sẵn sàng → fail-open)
     */
    boolean tryAcquire(String key, String token, Duration ttl);

    /**
     * Nhả lock — chỉ xoá khi `token` còn khớp. Gọi khi `tryAcquire` trả false là vô nghĩa nhưng vô
     * hại (token không khớp), nên use case chỉ cần gọi trong `finally` khi đã acquire thành công.
     */
    void release(String key, String token);
}
