package com.knowledgegym.presentation;

import com.knowledgegym.identity.domain.port.EmailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only email service — captures password reset codes in-memory
 * so integration tests can retrieve and verify the full forgot-password flow.
 */
@TestConfiguration
public class TestEmailServiceConfig {

    public static final ConcurrentHashMap<String, String> LAST_CODES = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, String> VERIFICATION_CODES = new ConcurrentHashMap<>();

    @Bean
    @Primary
    public EmailService testEmailService() {
        return new EmailService() {
            @Override
            public void sendPasswordResetCode(String email, String code) {
                LAST_CODES.put(email.toLowerCase(), code);
            }

            @Override
            public void sendEmailVerificationCode(String email, String code) {
                VERIFICATION_CODES.put(email.toLowerCase(), code);
            }
        };
    }
}