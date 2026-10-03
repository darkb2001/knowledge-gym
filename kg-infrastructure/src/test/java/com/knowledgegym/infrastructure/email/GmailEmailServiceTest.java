package com.knowledgegym.infrastructure.email;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GmailEmailServiceTest {
    @Test
    void smtpEnabledSendsResetCodeToTheRequestedRecipient() {
        JavaMailSender sender = mock(JavaMailSender.class);
        var service = new GmailEmailService(sender, "sender@example.com", true);
        service.sendPasswordResetCode("recipient@example.com", "123456");
        var mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("recipient@example.com");
        assertThat(mail.getValue().getFrom()).isEqualTo("sender@example.com");
        assertThat(mail.getValue().getText()).contains("123456", "10 phút");
    }

    @Test
    void smtpDisabledFailsClosedForVerificationAndReset() {
        JavaMailSender sender = mock(JavaMailSender.class);
        var service = new GmailEmailService(sender, "sender@example.com", false);
        assertThatThrownBy(() -> service.sendEmailVerificationCode("recipient@example.com", "123456"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.sendPasswordResetCode("recipient@example.com", "123456"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(sender);
    }

    @Test
    void verificationMailHasDistinctSubjectAndCorrectRecipient() {
        JavaMailSender sender = mock(JavaMailSender.class);
        var service = new GmailEmailService(sender, "sender@example.com", true);
        service.sendEmailVerificationCode("recipient@example.com", "123456");
        var mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("recipient@example.com");
        assertThat(mail.getValue().getSubject()).contains("Xác minh email");
        assertThat(mail.getValue().getText()).contains("123456", "10 phút");
    }

    @Test
    void smtpFailureDoesNotReportSuccess() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any(SimpleMailMessage.class));
        var service = new GmailEmailService(sender, "sender@example.com", true);
        assertThatThrownBy(() -> service.sendPasswordResetCode("recipient@example.com", "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to send password reset email");
    }
}
