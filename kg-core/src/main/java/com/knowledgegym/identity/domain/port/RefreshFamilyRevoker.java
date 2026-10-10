package com.knowledgegym.identity.domain.port;

import java.util.UUID;

/**
 * Thu hồi một refresh-token family **bền vững**, tách khỏi transaction đang gọi.
 *
 * <p>Lý do tồn tại: {@code RefreshTokenUseCase.execute} phát hiện reuse (token cũ quay lại sau khi
 * đã rotate) rồi ném {@code AuthException} để trả 401. Nếu việc thu hồi nằm trong chính
 * {@code @Transactional} đó thì lệnh UPDATE bị rollback theo exception → family **không** bị cắt,
 * và kẻ giữ token mới nhất vẫn dùng được. Port này đảm bảo revoke commit độc lập.
 *
 * <p>Khác với {@link RefreshTokenRepository#revokeFamily} (dùng trong logout/session management,
 * cần atomic chung với các ghi khác), implementation của port này phải:
 * <ul>
 *   <li>chạy trong transaction riêng ({@code REQUIRES_NEW});</li>
 *   <li>đánh dấu cả PostgreSQL (audit) lẫn Redis (chặn lookup O(1));</li>
 *   <li>không ném exception khi revoke thành công — bên gọi cần ném lỗi xác thực của chính nó.</li>
 * </ul>
 */
public interface RefreshFamilyRevoker {

    /** Revoke mọi refresh token chưa revoked của family và set cờ Redis. Idempotent. */
    void revokeDurably(UUID familyId);
}
