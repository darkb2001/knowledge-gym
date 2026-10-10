package com.knowledgegym.identity.domain.port;

import java.time.Instant;
import java.util.UUID;

public interface TokenService {
    String generateAccessToken(UUID userId, String role);

    /**
     * Refresh token cho một family MỚI — mốc neo của family chính là thời điểm gọi.
     * Dùng ở login/register/OAuth: mỗi lần đăng nhập sinh family mới.
     */
    String generateRefreshToken(UUID userId, UUID familyId);

    /**
     * Refresh token khi ROTATE trong family đã có: giữ nguyên mốc neo {@code familyIssuedAt} của lần
     * login đầu để absolute lifetime không bị gia hạn vô thời hạn bởi các lần rotate.
     */
    String generateRefreshToken(UUID userId, UUID familyId, Instant familyIssuedAt);

    TokenPayload verifyAccessToken(String token);

    RefreshTokenClaims verifyRefreshToken(String token);

    record TokenPayload(UUID userId, String role, String jti) {}

    /**
     * Refresh JWT mang userId + familyId — cần cho reuse detection/rotation.
     *
     * @param familyIssuedAt mốc login đầu tiên của family; {@code null} với token phát trước khi có
     *   claim này (không được suy diễn mốc khi thiếu, tránh đá oan phiên đang hoạt động).
     */
    record RefreshTokenClaims(UUID userId, UUID familyId, Instant familyIssuedAt) {}
}
