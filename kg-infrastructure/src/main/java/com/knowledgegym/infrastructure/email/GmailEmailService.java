package com.knowledgegym.infrastructure.email;

import com.knowledgegym.identity.domain.port.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Password-reset mail via SMTP (Gmail App Password or Brevo SMTP relay).
 * SMTP disabled: fail closed, never pretend an email was delivered.
 * Never log plaintext codes; tests use a dedicated capture adapter.
 *
 * Mailu self-host is deferred (ADR-004 / docs/19-m11b-ops.md); this adapter is the
 * accepted production path until Mailu DNS + RAM budget are ready.
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
    public void sendEmailVerificationCode(String email, String code) {
        if (!smtpEnabled) {
            throw new IllegalStateException("SMTP must be enabled to verify email ownership");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Knowledge Gym — Xác minh email");
        message.setText("Mã xác minh email của bạn: " + code
                + "\nMã có hiệu lực trong 10 phút. Nếu không yêu cầu, hãy bỏ qua email này.");
        try {
            mailSender.send(message);
        } catch (RuntimeException ex) {
            log.error("Email verification delivery failed");
            throw new IllegalStateException("Unable to send email verification code");
        }
    }

    @Override
    public void sendPasswordChangedNotice(String email) {
        if (!smtpEnabled) {
            throw new IllegalStateException("SMTP must be enabled to deliver password change notices");
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(email);
            message.setSubject("Knowledge Gym — Mật khẩu đã được thay đổi");
            message.setText("""
                    Mật khẩu của tài khoản Knowledge Gym này vừa được thay đổi.

                    Nếu là bạn: không cần làm gì thêm. Mọi phiên đăng nhập khác đã được đăng xuất.
                    Nếu KHÔNG phải bạn: hãy dùng ngay chức năng "Quên mật khẩu" để đặt lại và kiểm tra thiết bị của bạn.
                    """);
            mailSender.send(message);
            log.info("Password change notice sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send password change notice to {}", email, e);
            throw new IllegalStateException("Unable to send password change notice", e);
        }
    }

    @Override
    public void sendPasswordResetCode(String email, String code) {
        if (!smtpEnabled) {
            throw new IllegalStateException("SMTP must be enabled to deliver password reset codes");
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
            // Prod: không log code — tránh secret trong log aggregator
            log.error("Failed to send password reset email to {}", email, e);
            throw new IllegalStateException("Unable to send password reset email", e);
        }
    }
}