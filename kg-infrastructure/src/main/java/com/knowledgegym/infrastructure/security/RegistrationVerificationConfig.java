package com.knowledgegym.infrastructure.security;

import com.knowledgegym.identity.application.RegisterUseCase;
import com.knowledgegym.identity.application.VerifiedRegistrationUseCase;
import com.knowledgegym.identity.domain.port.EmailService;
import com.knowledgegym.identity.domain.port.EmailVerificationPort;
import com.knowledgegym.identity.domain.port.UserRepository;
import com.knowledgegym.identity.domain.port.PasswordHasher;
import com.knowledgegym.identity.domain.port.RefreshTokenRepository;
import com.knowledgegym.identity.domain.port.RefreshTokenCachePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationVerificationConfig {
    @Bean
    VerifiedRegistrationUseCase verifiedRegistrationUseCase(RegisterUseCase registration,
            UserRepository users, EmailVerificationPort challenges, EmailService mail,
            PasswordHasher passwords, RefreshTokenRepository refreshTokens, RefreshTokenCachePort cache) {
        return new VerifiedRegistrationUseCase(registration, users, challenges, mail, passwords, refreshTokens, cache);
    }
}
