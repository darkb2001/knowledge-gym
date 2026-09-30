package com.knowledgegym.infrastructure.config;

import com.knowledgegym.content.application.CatalogQueryUseCase;
import com.knowledgegym.content.application.GetQuestionDetailUseCase;
import com.knowledgegym.content.application.ImportContentUseCase;
import com.knowledgegym.content.application.ManageQuestionsUseCase;
import com.knowledgegym.content.application.QueryQuestionsUseCase;
import com.knowledgegym.content.domain.port.AnswerHtmlSanitizer;
import com.knowledgegym.content.domain.port.ContentSource;
import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.content.domain.port.TopicRepository;
import com.knowledgegym.identity.application.*;
import com.knowledgegym.identity.domain.port.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
    LogoutUseCase logoutUseCase(TokenService tokenService,
                                 RefreshTokenRepository refreshTokenRepository,
                                 RefreshTokenCachePort cache) {
        return new LogoutUseCase(tokenService, refreshTokenRepository, cache);
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

    // ------------------------------------------------------------------ content (m4)

    @Bean
    ImportContentUseCase importContentUseCase(ContentSource contentSource,
                                              TopicRepository topicRepository,
                                              ModuleRepository moduleRepository,
                                              QuestionRepository questionRepository) {
        return new ImportContentUseCase(contentSource, topicRepository, moduleRepository, questionRepository);
    }

    @Bean
    QueryQuestionsUseCase queryQuestionsUseCase(QuestionRepository questionRepository) {
        return new QueryQuestionsUseCase(questionRepository);
    }

    @Bean
    GetQuestionDetailUseCase getQuestionDetailUseCase(QuestionRepository questionRepository,
                                                     ModuleRepository moduleRepository) {
        return new GetQuestionDetailUseCase(questionRepository, moduleRepository);
    }

    @Bean
    ManageQuestionsUseCase manageQuestionsUseCase(QuestionRepository questionRepository,
                                                   ModuleRepository moduleRepository,
                                                   AnswerHtmlSanitizer answerHtmlSanitizer) {
        return new ManageQuestionsUseCase(questionRepository, moduleRepository, answerHtmlSanitizer);
    }

    @Bean
    CatalogQueryUseCase catalogQueryUseCase(TopicRepository topicRepository,
                                            ModuleRepository moduleRepository) {
        return new CatalogQueryUseCase(topicRepository, moduleRepository);
    }

    /**
     * Executor riêng cho import (đẩy vào nền ~ vài giây). Không dùng chung với task khác để
     * 1 lần import nặng không chiếm hết pool của mail/notification.
     * Phía inject phải dùng `@Qualifier("contentImportExecutor")` — Spring Boot cũng tạo
     * `applicationTaskExecutor` nên có nhiều `TaskExecutor` trong context.
     *
     * Kiểu trả về là `TaskExecutor` (không phải `Executor`): Spring suy bean type từ return type
     * của factory method, nên khai `Executor` sẽ không autowire được vào chỗ cần `TaskExecutor`.
     */
    @Bean(name = "contentImportExecutor")
    TaskExecutor contentImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("content-import-");
        executor.initialize();
        return executor;
    }
}
