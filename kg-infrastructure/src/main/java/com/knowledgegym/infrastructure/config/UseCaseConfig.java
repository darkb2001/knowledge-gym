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
import com.knowledgegym.learning.application.EnrollCardsUseCase;
import com.knowledgegym.learning.application.QueryDueUseCase;
import com.knowledgegym.learning.application.ReviewCardUseCase;
import com.knowledgegym.learning.domain.port.SRSCardRepository;
import com.knowledgegym.learning.domain.port.SrsDeckRepository;
import com.knowledgegym.learning.domain.port.StudyAttemptRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import com.knowledgegym.content.application.GenerateQuestionOptionsUseCase;
import com.knowledgegym.content.domain.port.QuestionOptionRepository;
import com.knowledgegym.learning.application.*;
import com.knowledgegym.learning.application.strategy.*;
import com.knowledgegym.learning.domain.model.QuizStrategy;
import com.knowledgegym.learning.domain.port.*;
import com.knowledgegym.shared.domain.port.DistributedLockPort;
import com.knowledgegym.progress.application.*;
import com.knowledgegym.progress.domain.port.*;
import java.util.Map;
import java.time.ZoneId;


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
                                 RefreshTokenCachePort cache,
                                 SessionInvalidationPort sessionInvalidation) {
        return new LogoutUseCase(tokenService, refreshTokenRepository, cache, sessionInvalidation);
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
                                               RefreshTokenCachePort cache,
                                               SessionInvalidationPort sessionInvalidation) {
        return new ResetPasswordUseCase(userRepository, codeRepository, passwordHasher,
                refreshTokenRepository, cache, sessionInvalidation);
    }

    @Bean
    ChangePasswordUseCase changePasswordUseCase(UserRepository userRepository,
                                               PasswordHasher passwordHasher,
                                               RefreshTokenRepository refreshTokenRepository,
                                               RefreshTokenCachePort cache,
                                               EmailService emailService,
                                               SecurityEventPort securityEvents) {
        return new ChangePasswordUseCase(userRepository, passwordHasher, refreshTokenRepository,
                cache, emailService, securityEvents);
    }

    @Bean
    SetPasswordUseCase setPasswordUseCase(UserRepository userRepository,
                                          PasswordResetCodeRepository codeRepository,
                                          PasswordHasher passwordHasher,
                                          EmailService emailService,
                                          SecurityEventPort securityEvents) {
        return new SetPasswordUseCase(userRepository, codeRepository, passwordHasher,
                emailService, securityEvents);
    }

    // ------------------------------------------------------------------ content (m4)

    @Bean
    ImportContentUseCase importContentUseCase(ContentSource contentSource,
                                              TopicRepository topicRepository,
                                              ModuleRepository moduleRepository,
                                              QuestionRepository questionRepository, GenerateQuestionOptionsUseCase options,
                                              com.knowledgegym.content.domain.port.ContentTrackRepository trackRepository) {
        return new ImportContentUseCase(contentSource, topicRepository, moduleRepository, questionRepository,
                options, trackRepository);
    }

    @Bean
    QueryQuestionsUseCase queryQuestionsUseCase(QuestionRepository questionRepository) {
        return new QueryQuestionsUseCase(questionRepository);
    }

    @Bean
    GetQuestionDetailUseCase getQuestionDetailUseCase(QuestionRepository questionRepository,
                                                     ModuleRepository moduleRepository, QuestionOptionRepository options) {
        return new GetQuestionDetailUseCase(questionRepository, moduleRepository, options);
    }

    @Bean
    ManageQuestionsUseCase manageQuestionsUseCase(QuestionRepository questionRepository,
                                                   ModuleRepository moduleRepository,
                                                   AnswerHtmlSanitizer answerHtmlSanitizer) {
        return new ManageQuestionsUseCase(questionRepository, moduleRepository, answerHtmlSanitizer);
    }

    @Bean
    com.knowledgegym.learning.application.AdminLearningUseCase adminLearningUseCase(
            com.knowledgegym.learning.domain.port.AdminLearningPort learning) {
        return new com.knowledgegym.learning.application.AdminLearningUseCase(learning);
    }

    @Bean
    com.knowledgegym.identity.application.ManageUsersUseCase manageUsersUseCase(
            com.knowledgegym.identity.domain.port.AdminUserPort users) {
        return new com.knowledgegym.identity.application.ManageUsersUseCase(users);
    }

    @Bean com.knowledgegym.content.application.ManageCatalogUseCase manageCatalogUseCase(TopicRepository topics, ModuleRepository modules) {
        return new com.knowledgegym.content.application.ManageCatalogUseCase(topics, modules);
    }

    @Bean
    CatalogQueryUseCase catalogQueryUseCase(TopicRepository topicRepository,
                                            ModuleRepository moduleRepository,
                                            com.knowledgegym.content.domain.port.ContentTrackRepository trackRepository) {
        return new CatalogQueryUseCase(topicRepository, moduleRepository, trackRepository);
    }

    @Bean GenerateQuestionOptionsUseCase generateQuestionOptionsUseCase(ModuleRepository m, QuestionRepository q, QuestionOptionRepository o) {return new GenerateQuestionOptionsUseCase(m,q,o);}
    @Bean Map<QuizStrategy, QuizGenerationStrategy> quizStrategies(QuestionRepository q, ModuleRepository m, StudyAttemptRepository a, SRSCardRepository s, Clock c) {
        var pool=new QuizCandidatePool(q,m);
        return Map.of(QuizStrategy.RANDOM,new RandomQuizStrategy(pool),QuizStrategy.WEAKNESS,new WeaknessQuizStrategy(pool,a),QuizStrategy.INTERVIEW,new InterviewQuizStrategy(pool),QuizStrategy.SPACED,new SpacedQuizStrategy(pool,s,c));
    }
    /**
     * `ThreadLocalRandom.current()` **cố ý** ở đây: mỗi lần gọi, JDK trả về generator của thread đang
     * chạy, nên bean singleton này vẫn random trên từng request thread. Thay bằng
     * `RandomGenerator.getDefault()` sẽ là **regression**: nó trả một instance chia sẻ, và dù hợp đồng
     * `RandomGenerator` không hứa thread-safe, JDK hiện dùng `L32X64MixRandom` với state không đồng bộ
     * → nhiều request đồng thời có thể nhận cùng seed (đo được: 4 thread × 20k lần chỉ ra 1/1000 giá
     * trị). Với quiz thì "random" sẽ trùng lặp giữa các user.
     */
    @Bean GenerateQuizUseCase generateQuizUseCase(QuestionRepository q, QuestionOptionRepository o, QuizSessionRepository s, Map<QuizStrategy, QuizGenerationStrategy> strategies, Clock c){return new GenerateQuizUseCase(q,o,s,strategies,java.util.concurrent.ThreadLocalRandom.current(),c);}
    @Bean SubmitQuizUseCase submitQuizUseCase(QuizSessionRepository s, QuestionRepository q, QuestionOptionRepository o, RecordAttemptUseCase recordAttempts, DistributedLockPort l, Clock c){return new SubmitQuizUseCase(s,q,o,recordAttempts,l,c);}
    @Bean QueryQuizUseCase queryQuizUseCase(QuizSessionRepository s, QuestionRepository q, QuestionOptionRepository o){return new QueryQuizUseCase(s,q,o);}
    @Bean MockInterviewUseCase mockInterviewUseCase(InterviewSessionRepository s, QuestionRepository q, ModuleRepository m, TopicRepository t, Clock c){return new MockInterviewUseCase(s,q,m,t,c);}

    @Bean
    ZoneId progressTimezone(AppProgressProperties properties) {
        return properties.getTimezone();
    }

    @Bean
    RecordAttemptUseCase recordAttemptUseCase(StudyAttemptRepository attempts,
            QuestionModulePort questionModules, UserXpRepository xp, UserProgressRepository progress) {
        return new RecordAttemptUseCase(attempts, questionModules, xp, progress);
    }

    @Bean
    QueryProgressUseCase queryProgressUseCase(UserProgressRepository progress, UserXpRepository xp,
            StudyAttemptAnalytics analytics, ModuleRepository modules, ZoneId progressTimezone, Clock clock) {
        return new QueryProgressUseCase(progress, xp, analytics, modules, progressTimezone, clock);
    }

    @Bean
    QueryMindmapUseCase queryMindmapUseCase(ModuleRepository modules, UserProgressRepository progress) {
        return new QueryMindmapUseCase(modules, progress);
    }

    @Bean
    QueryHeatmapUseCase queryHeatmapUseCase(StudyAttemptAnalytics analytics, ZoneId progressTimezone, Clock clock) {
        return new QueryHeatmapUseCase(analytics, progressTimezone, clock);
    }

    @Bean
    QueryLeaderboardUseCase queryLeaderboardUseCase(LeaderboardPort leaderboard) {
        return new QueryLeaderboardUseCase(leaderboard);
    }

    // ------------------------------------------------------------------ notes (m8)

    @Bean
    com.knowledgegym.notes.application.NotesUseCase notesUseCase(
            com.knowledgegym.notes.domain.port.NoteRepository noteRepository) {
        return new com.knowledgegym.notes.application.NotesUseCase(noteRepository);
    }

    // ------------------------------------------------------------------ global search (m11)

    /**
     * The index is injected as an {@link java.util.Optional} so this bean exists whether or not
     * Elasticsearch is enabled. Search is a read path and the Postgres query already answers it,
     * so the flag only decides which backend is preferred — not whether the endpoint exists.
     *
     * <p>The qualifier matters: when the flag is on both adapters implement {@code SearchQueryPort},
     * and without it Spring would fail to choose between them.
     */
    @Bean
    com.knowledgegym.search.application.GlobalSearchUseCase globalSearchUseCase(
            @org.springframework.beans.factory.annotation.Qualifier("postgresSearchQuery")
            com.knowledgegym.search.domain.port.SearchQueryPort postgresSearch,
            @org.springframework.beans.factory.annotation.Qualifier("elasticsearchSearchQuery")
            java.util.Optional<com.knowledgegym.search.domain.port.SearchQueryPort> searchIndex,
            com.knowledgegym.search.domain.port.SearchModeSettingsPort searchSettings) {
        return new com.knowledgegym.search.application.GlobalSearchUseCase(postgresSearch, searchIndex, searchSettings);
    }

    @Bean
    com.knowledgegym.search.application.SearchModeUseCase searchModeUseCase(
            com.knowledgegym.search.domain.port.SearchModeSettingsPort settings,
            com.knowledgegym.search.domain.port.SearchAuditPort audit) {
        return new com.knowledgegym.search.application.SearchModeUseCase(settings, audit);
    }

    @Bean
    com.knowledgegym.search.application.ElasticsearchLifecycleUseCase elasticsearchLifecycleUseCase(
            com.knowledgegym.search.application.SearchModeUseCase modes,
            com.knowledgegym.shared.domain.port.HostScriptPort hostScriptPort,
            com.knowledgegym.search.domain.port.SearchAuditPort audit) {
        return new com.knowledgegym.search.application.ElasticsearchLifecycleUseCase(modes, hostScriptPort, audit);
    }

    @Bean
    GetCurrentUserUseCase getCurrentUserUseCase(UserRepository userRepository) {
        return new GetCurrentUserUseCase(userRepository);
    }

    @Bean
    UpdateProfileUseCase updateProfileUseCase(UserRepository userRepository,
            @org.springframework.beans.factory.annotation.Value("${app.api.public-base-url:http://localhost:8080}")
            String apiBaseUrl) {
        // Ảnh đại diện do API phục vụ (AvatarController), không phải host S3: prefix hợp lệ
        // chính là base URL của API.
        java.util.Optional<String> prefix = apiBaseUrl == null || apiBaseUrl.isBlank()
                ? java.util.Optional.empty()
                : java.util.Optional.of(apiBaseUrl.replaceAll("/+$", "") + "/");
        return new UpdateProfileUseCase(userRepository, prefix);
    }

    /** Cùng property với adapter + controller: thiếu Garage thì endpoint avatar biến mất, app vẫn boot. */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "storage.s3.enabled", havingValue = "true")
    AvatarUseCase avatarUseCase(UserRepository userRepository,
            com.knowledgegym.shared.domain.port.StoragePort storage,
            @org.springframework.beans.factory.annotation.Value("${storage.s3.objects-bucket:kg-local}") String bucket,
            @org.springframework.beans.factory.annotation.Value("${app.api.public-base-url:http://localhost:8080}")
            String publicBaseUrl) {
        return new AvatarUseCase(userRepository, storage, bucket, publicBaseUrl);
    }

    // ------------------------------------------------------------------ learning / SRS (m5)

    /**
     * `Clock` bean để use case không đọc đồng hồ hệ thống trực tiếp — test set `next_review`/
     * `last_reviewed_at` theo thời điểm cố định thay vì phụ thuộc ngày chạy.
     */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    EnrollCardsUseCase enrollCardsUseCase(QuestionRepository questionRepository,
                                          ModuleRepository moduleRepository,
                                          SRSCardRepository srsCardRepository,
                                          SrsDeckRepository srsDeckRepository,
                                          Clock clock) {
        return new EnrollCardsUseCase(questionRepository, moduleRepository,
                srsCardRepository, srsDeckRepository, clock);
    }

    @Bean
    QueryDueUseCase queryDueUseCase(SRSCardRepository srsCardRepository,
                                    QuestionRepository questionRepository,
                                    ModuleRepository moduleRepository,
                                    Clock clock) {
        return new QueryDueUseCase(srsCardRepository, questionRepository, moduleRepository, clock);
    }

    @Bean
    ReviewCardUseCase reviewCardUseCase(SRSCardRepository srsCardRepository,
                                        RecordAttemptUseCase recordAttemptUseCase,
                                        Clock clock) {
        return new ReviewCardUseCase(srsCardRepository, recordAttemptUseCase, clock);
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
