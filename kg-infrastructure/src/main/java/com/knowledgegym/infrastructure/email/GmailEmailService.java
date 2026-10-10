package com.knowledgegym.infrastructure.email;

import com.knowledgegym.identity.domain.port.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Thư giao dịch qua SMTP (Gmail App Password hoặc relay có domain riêng như Brevo).
 *
 * <p>Thư là HTML + bản text dự phòng, có tên hiển thị người gửi ("Knowledge Gym") vì thư
 * trơ địa chỉ gmail và nội dung plain-text trông như thư rác. Ảnh đại diện hiện bên cạnh
 * người gửi trong hộp thư KHÔNG do header quyết định: Gmail lấy ảnh của tài khoản Google
 * đang gửi, hoặc logo domain nếu domain có BIMI + VMC (chứng chỉ trả phí). Xem docs/24-mail-prod.md.
 *
 * <p>SMTP tắt: fail closed, không giả vờ đã gửi. Không bao giờ log mã dạng plaintext.
 */
@Component
public class GmailEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(GmailEmailService.class);

    /** Trùng màu accent của web (app/globals.css) để thư nhìn cùng một hệ thương hiệu. */
    private static final String ACCENT = "#345f73";
    private static final String INK = "#1f2937";
    private static final String MUTED = "#6b7280";
    private static final String LINE = "#e5e7eb";
    private static final String BRAND = "Knowledge Gym";

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;
    private final boolean smtpEnabled;

    public GmailEmailService(
            JavaMailSender mailSender,
            @Value("${app.email.from:no-reply@knowledgegym.dev}") String fromAddress,
            @Value("${app.email.from-name:Knowledge Gym}") String fromName,
            @Value("${app.email.smtp-enabled:false}") boolean smtpEnabled) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.smtpEnabled = smtpEnabled;
    }

    @Override
    public void sendEmailVerificationCode(String email, String code) {
        requireSmtp("verify email ownership");
        send(email,
                BRAND + " — Xác minh email",
                "Xác minh địa chỉ email",
                "Nhập mã dưới đây để hoàn tất đăng ký tài khoản " + BRAND + ".",
                code,
                "Mã có hiệu lực trong 10 phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.",
                "Mã xác minh email " + BRAND + " của bạn: " + code
                        + "\nMã có hiệu lực trong 10 phút. Nếu không yêu cầu, hãy bỏ qua email này.");
    }

    @Override
    public void sendPasswordResetCode(String email, String code) {
        requireSmtp("deliver password reset codes");
        send(email,
                BRAND + " — Mã đặt lại mật khẩu",
                "Đặt lại mật khẩu",
                "Dùng mã dưới đây để đặt mật khẩu mới cho tài khoản " + BRAND + " của bạn.",
                code,
                "Mã có hiệu lực trong 10 phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.",
                "Mã đặt lại mật khẩu " + BRAND + " của bạn là: " + code
                        + "\n\nMã có hiệu lực trong 10 phút.\nNếu bạn không yêu cầu, hãy bỏ qua email này.");
        log.info("Password reset email delivered");
    }

    @Override
    public void sendPasswordChangedNotice(String email) {
        requireSmtp("deliver password change notices");
        send(email,
                BRAND + " — Mật khẩu đã được thay đổi",
                "Mật khẩu đã được thay đổi",
                "Mật khẩu của tài khoản " + BRAND + " này vừa được thay đổi.",
                null,
                "Nếu là bạn: không cần làm gì thêm, mọi phiên đăng nhập khác đã được đăng xuất. "
                        + "Nếu KHÔNG phải bạn: hãy dùng ngay chức năng \"Quên mật khẩu\" để đặt lại và kiểm tra thiết bị của bạn.",
                BRAND + " — Mật khẩu đã được thay đổi"
                        + "\n\nMật khẩu của tài khoản này vừa được thay đổi."
                        + "\nNếu là bạn: không cần làm gì thêm. Mọi phiên đăng nhập khác đã được đăng xuất."
                        + "\nNếu KHÔNG phải bạn: hãy dùng ngay chức năng \"Quên mật khẩu\" để đặt lại và kiểm tra thiết bị của bạn.");
        log.info("Password change notice delivered");
    }

    private void requireSmtp(String purpose) {
        if (!smtpEnabled) {
            throw new IllegalStateException("SMTP must be enabled to " + purpose);
        }
    }

    private void send(String to, String subject, String heading, String intro, String code, String note, String textBody) {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            // Tên hiển thị là phần duy nhất còn giữ được thương hiệu khi Gmail bắt buộc
            // header From về địa chỉ tài khoản gửi.
            helper.setFrom(fromAddress, fromName);
            helper.setReplyTo(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textBody, layout(heading, intro, code, note));
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException e) {
            log.error("Transactional email delivery failed", e);
            throw new IllegalStateException("Unable to send email", e);
        }
    }

    /** HTML đơn giản, style inline: hộp thư nào cũng hiển thị được, không cần tải ảnh ngoài. */
    String layout(String heading, String intro, String code, String note) {
        String codeBlock = code == null ? "" : """
                    <div style="margin:0 0 18px;padding:16px;border:1px solid %s;border-radius:12px;background:#f8fafb;text-align:center">
                      <span style="font-size:28px;font-weight:700;letter-spacing:8px;color:%s">%s</span>
                    </div>
                """.formatted(LINE, INK, code);
        return """
                <!doctype html>
                <html lang="vi"><head><meta charset="utf-8">
                <meta name="color-scheme" content="light dark"><title>%s</title></head>
                <body style="margin:0;padding:24px;background:#f4f5f6;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:%s">
                  <div style="max-width:520px;margin:0 auto">
                    <div style="padding:0 4px 14px;font-size:17px;font-weight:700;color:%s">%s<span style="color:%s">.</span></div>
                    <div style="background:#ffffff;border:1px solid %s;border-radius:14px;padding:24px">
                      <div style="height:3px;width:44px;background:%s;border-radius:2px;margin:0 0 16px"></div>
                      <h1 style="margin:0 0 12px;font-size:20px;line-height:1.35;color:%s">%s</h1>
                      <p style="margin:0 0 18px;font-size:15px;line-height:1.6;color:%s">%s</p>
                      %s
                      <p style="margin:18px 0 0;font-size:13px;line-height:1.6;color:%s">%s</p>
                    </div>
                    <p style="margin:16px 4px 0;font-size:12px;line-height:1.6;color:%s">
                      Thư tự động từ %s — vui lòng không trả lời trực tiếp.<br>
                      Bạn nhận được thư này vì có yêu cầu gắn với địa chỉ email này.
                    </p>
                  </div>
                </body></html>
                """.formatted(subjectSafe(heading), INK, INK, BRAND, ACCENT, LINE, ACCENT, INK, heading, MUTED, intro, codeBlock, MUTED, note, MUTED, BRAND);
    }

    private static String subjectSafe(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
