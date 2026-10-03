package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.PasswordResetCode;
import com.knowledgegym.identity.domain.port.PasswordResetCodeRepository;

import java.util.UUID;

/**
 * Xác minh mã 6 số dùng chung cho `reset-password` và `set-password` (user Google đặt mật khẩu lần đầu).
 * Code là single-use, TTL 10 phút, tối đa 5 lần thử (PasswordResetCode.MAX_ATTEMPTS) — dùng hết lượt
 * thì vô hiệu hoá luôn. Hash SHA-256, không so sánh plaintext.
 *
 * Không gửi mã: user đã đăng nhập vẫn phải qua email → chứng minh sở hữu hộp thư (chuẩn Notion/Slack/Google).
 */
final class PasswordResetCodeVerifier {

    private PasswordResetCodeVerifier() {}

    /**
     * Trả về code entity ĐÃ verify nhưng CHƯA markUsed — caller markUsed sau khi đổi mật khẩu thành công
     * (tránh trường hợp lỗi giữa chừng làm user mất mã mà chưa đổi được gì).
     */
    static PasswordResetCode verify(PasswordResetCodeRepository codeRepository, UUID userId, String code) {
        PasswordResetCode stored = codeRepository.findLatestActiveByUserId(userId)
                .orElseThrow(() -> new AuthException(AuthException.Kind.BAD_REQUEST, "Invalid reset code"));

        if (stored.isExpired() || stored.isUsed() || stored.isAttemptsExhausted()) {
            throw new AuthException(AuthException.Kind.BAD_REQUEST, "Reset code expired or already used");
        }

        if (code == null || !HashUtils.sha256Hex(code).equals(stored.getCodeHash())) {
            stored.incrementAttempts();
            if (stored.isAttemptsExhausted()) {
                stored.markUsed();
            }
            codeRepository.save(stored);
            throw new AuthException(AuthException.Kind.BAD_REQUEST, "Invalid reset code");
        }

        return stored;
    }
}
