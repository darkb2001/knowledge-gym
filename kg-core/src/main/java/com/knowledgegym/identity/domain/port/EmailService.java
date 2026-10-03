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
}