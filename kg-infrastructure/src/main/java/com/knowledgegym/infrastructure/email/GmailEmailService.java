package com.knowledgegym.infrastructure.email;

import com.knowledgegym.identity.domain.port.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Gửi mã password reset qua Gmail SMTP (App Password).
 * Fallback: nếu SMTP chưa cấu hình (dev profile, thiếu App Password), log code ra console.
 */
@Component
public class GmailEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(GmailEmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean smtpEnabled;

    public GmailEmailService(
            JavaMailSender mailSender,
            @Value("${app.email.from:no-reply@knowledgegym.dev}") String fromAddress,
            @Value("${app.email.smtp-enabled:false}") boolean smtpEnabled) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.smtpEnabled = smtpEnabled;
    }

    @Override
    public void sendPasswordResetCode(String email, String code) {
        if (!smtpEnabled) {
            // Dev fallback — vẫn log để test có thể lấy code
            log.warn("[DEV-FALLBACK] Password reset code for {}: {}", email, code);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(email);
            message.setSubject("Knowledge Gym — Mã đặt lại mật khẩu");
            message.setText("""
                    Mã đặt lại mật khẩu của bạn là: %s

                    Mã có hiệu lực trong 10 phút.
                    Nếu bạn không yêu cầu, hãy bỏ qua email này.
                    """.formatted(code));
            mailSender.send(message);
            log.info("Password reset code sent to {}", email);
        } catch (Exception e) {
            // Fallback: log thay vì fail request (plan: Gmail SMTP hay bị block)
            log.error("Failed to send reset email to {}, falling back to console log. Code: {}",
                    email, code, e);
        }
    }
}