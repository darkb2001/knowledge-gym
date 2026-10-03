package com.knowledgegym.identity.domain.port;

/**
 * Gateway cho gửi email — implement bằng Spring Boot Starter Mail (Gmail SMTP).
 * Dev profile: log code ra console nếu SMTP unavailable.
 */
public interface EmailService {
    void sendPasswordResetCode(String email, String code);

    default void sendEmailVerificationCode(String email, String code) {
        throw new IllegalStateException("Email verification delivery is not configured");
    }

    /**
     * Thông báo "mật khẩu đã thay đổi" (chuẩn mọi app: mail cảnh báo để user kịp phản ứng nếu không phải mình).
     * Best-effort — adapter không hỗ trợ thì no-op, người dùng vẫn đổi được mật khẩu.
     */
    default void sendPasswordChangedNotice(String email) {
    }
}