package com.knowledgegym.infrastructure.email;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class EmailOutboxServiceTest {
    @Test
    void verificationAndResetUseSeparateOutboxEventTypes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var service = new EmailOutboxService(jdbc, new ObjectMapper());

        service.sendEmailVerificationCode("user@example.com", "123456");
        service.sendPasswordResetCode("user@example.com", "654321");

        var type = ArgumentCaptor.forClass(String.class);
        var payload = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).update(anyString(), any(), type.capture(), payload.capture());
        assertThat(type.getAllValues()).containsExactly("email.verification", "email.password-reset");
        assertThat(payload.getAllValues().getFirst()).contains("user@example.com", "123456");
        assertThat(payload.getAllValues().get(1)).contains("user@example.com", "654321");
    }
}
