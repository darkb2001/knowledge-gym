package com.knowledgegym.infrastructure.email;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Thư giao dịch: kiểm envelope (người nhận, tiêu đề, tên hiển thị người gửi) và phần HTML
 * qua seam {@code layout} — nội dung MIME thật được nghiệm thu bằng gửi thật + đọc lại qua IMAP
 * (xem docs/24-mail-prod.md), vì test classpath có hai bản jakarta.mail nên không đọc
 * {@code getContent()} của MimeMessage ở đây.
 */
class GmailEmailServiceTest {

    private static final String FROM = "sender@example.com";

    @Test
    void smtpEnabledSendsResetCodeToTheRequestedRecipient() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        var service = new GmailEmailService(sender, FROM, "Knowledge Gym", true);
        service.sendPasswordResetCode("recipient@example.com", "123456");
        var mail = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(mail.capture());
        MimeMessage message = mail.getValue();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("recipient@example.com");
        // Tên hiển thị là thứ duy nhất còn giữ thương hiệu khi Gmail ghi đè địa chỉ From.
        assertThat(message.getFrom()[0].toString()).contains("Knowledge Gym", FROM);
        assertThat(message.getSubject()).contains("đặt lại mật khẩu");
    }

    @Test
    void htmlShowsBrandHighlightAndCodeWithoutRemoteImages() {
        var service = new GmailEmailService(mock(JavaMailSender.class), FROM, "Knowledge Gym", true);
        String html = service.layout("Đặt lại mật khẩu", "Dùng mã dưới đây.", "123456", "Mã có hiệu lực trong 10 phút.");
        assertThat(html).contains("Knowledge Gym", "#345f73", "letter-spacing", "123456", "10 phút", "<html");
        assertThat(html).doesNotContain("<img", "http://", "https://");
    }

    @Test
    void htmlOmitsCodeBlockWhenThereIsNoCode() {
        var service = new GmailEmailService(mock(JavaMailSender.class), FROM, "Knowledge Gym", true);
        String html = service.layout("Mật khẩu đã được thay đổi", "Mật khẩu vừa được thay đổi.", null, "Nếu không phải bạn, hãy đặt lại mật khẩu.");
        assertThat(html).contains("Mật khẩu đã được thay đổi");
        assertThat(html).doesNotContain("letter-spacing");
    }

    @Test
    void smtpDisabledFailsClosedForVerificationAndReset() {
        JavaMailSender sender = mock(JavaMailSender.class);
        var service = new GmailEmailService(sender, FROM, "Knowledge Gym", false);
        assertThatThrownBy(() -> service.sendEmailVerificationCode("recipient@example.com", "123456"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.sendPasswordResetCode("recipient@example.com", "123456"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(sender);
    }

    @Test
    void verificationMailHasDistinctSubject() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        var service = new GmailEmailService(sender, FROM, "Knowledge Gym", true);
        service.sendEmailVerificationCode("recipient@example.com", "123456");
        var mail = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(mail.capture());
        assertThat(mail.getValue().getSubject()).contains("Xác minh email");
        assertThat(mail.getValue().getAllRecipients()[0].toString()).isEqualTo("recipient@example.com");
    }

    @Test
    void smtpFailureDoesNotReportSuccess() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any(MimeMessage.class));
        var service = new GmailEmailService(sender, FROM, "Knowledge Gym", true);
        assertThatThrownBy(() -> service.sendPasswordResetCode("recipient@example.com", "123456"))
                .isInstanceOf(IllegalStateException.class);
    }
}
