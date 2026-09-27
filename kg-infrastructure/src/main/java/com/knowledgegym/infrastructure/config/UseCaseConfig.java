package com.knowledgegym.infrastructure.config;

import com.knowledgegym.identity.application.*;
import com.knowledgegym.identity.domain.port.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Use case beans — DDD application services.
 * Constructor injection thay vì @Service (@Service đặt trên adapter, use case thuần Java).
 */
@Configuration
public class UseCaseConfig {

    @Bean
    RegisterUseCase registerUseCase(UserRepository userRepository, PasswordHasher passwordHasher) {
        return new RegisterUseCase(userRepository, passwordHasher);
    }

    @Bean
    LoginUseCase loginUseCase(UserRepository userRepository, PasswordHasher passwordHasher,
                               TokenService tokenService, RefreshTokenRepository refreshTokenRepository,
                               RefreshTokenCachePort cache) {
        return new LoginUseCase(userRepository, passwordHasher, tokenService, refreshTokenRepository, cache);
    }

    @Bean
    RefreshTokenUseCase refreshTokenUseCase(TokenService tokenService,
                                             RefreshTokenRepository refreshTokenRepository,
                                             RefreshTokenCachePort cache,
                                             UserRepository userRepository) {
        return new RefreshTokenUseCase(tokenService, refreshTokenRepository, cache, userRepository);
    }

    @Bean
    LogoutUseCase logoutUseCase(RefreshTokenRepository refreshTokenRepository, RefreshTokenCachePort cache) {
        return new LogoutUseCase(refreshTokenRepository, cache);
    }

    @Bean
    ForgotPasswordUseCase forgotPasswordUseCase(UserRepository userRepository,
                                                  PasswordResetCodeRepository codeRepository,
                                                  EmailService emailService) {
        return new ForgotPasswordUseCase(userRepository, codeRepository, emailService);
    }

    @Bean
    ResetPasswordUseCase resetPasswordUseCase(UserRepository userRepository,
                                               PasswordResetCodeRepository codeRepository,
                                               PasswordHasher passwordHasher,
                                               RefreshTokenRepository refreshTokenRepository,
                                               RefreshTokenCachePort cache) {
        return new ResetPasswordUseCase(userRepository, codeRepository, passwordHasher,
                refreshTokenRepository, cache);
    }
}