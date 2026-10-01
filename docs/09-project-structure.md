# Project Structure — Knowledge Gym

## Current vs target (sau m7 Progress + Dashboard)

Tree dài bên dưới = **roadmap target** (đủ context content/learning/…). Phần này ghi **đã có thật trong repo** sau m7 — tránh nhầm scaffold tương lai với code đang chạy.

| Module | Đã có (m5) | Chưa (m6+) |
|--------|------------|------------|
| `kg-core` | `identity/` (auth use cases); `content/` (models/ports + catalog/import/query); `learning/` (SRS, Quiz, Mock Interview TEXT); `progress/` (attempt recording, XP/mastery policy, dashboard queries); `shared/` | notes, blog use cases |
| `kg-infrastructure` | Flyway V001–**V018** (V018 backfills XP/mastery from historical attempts); JPA adapters for content/learning/progress; atomic progress upsert + XP/advisory-lock adapter; Redis `lb:global` cache-aside; Jsoup content import; Caffeine; JWT/OAuth2/cookie; refresh cache; Bucket4j rate limit; SMTP | AI writer, collectors |
| `kg-presentation` | REST auth/content/SRS/quiz/interview + **dashboard** (radar, heatmap, leaderboard, user progress/stats); RFC 7807 advice; caching/OpenAPI config; boot app | notes/blog controllers, websocket |
| **`kg-frontend`** | Next.js 14 App Router + TS + Tailwind; auth, question browser/detail, flashcards, quiz, interview, **`/dashboard`** (radar + heatmap + leaderboard); in-memory JWT + `ensureAccessToken` | — |
| `kg-agent` | module skeleton | schedulers |

**ArchUnit (đang enforce):**
- `PresentationLayerArchTest` — `..presentation..` ✗ `..infrastructure.persistence..` và ✗ JPA repos; ✗ `..infrastructure.content` (để tránh presentation phụ thuộc adapter content); cho phép `infrastructure.security` helpers như cookie/IP ở composition root
- `DomainLayerArchTest` — domain package ✗ Spring / JPA

**IP audit helper:** `ClientIpResolver` (`trust-forwarded-headers`) dùng chung AuthController, RateLimitFilter, OAuth2SuccessHandler.

### Package mới ở m4a

**`kg-core` — `com.knowledgegym.content`** (thuần Java, zero Spring/JPA):
- `domain/model/` — `Question`, `QuestionOption`, `Topic`, `ModuleRef`, `TopicWithStats`, `ModuleWithStats`, `ContentCatalog`, `ParsedQuestion`, `QuestionQuery`
- `domain/port/` — `QuestionRepository`, `TopicRepository`, `ModuleRepository`, `ContentSource`, `AnswerHtmlSanitizer`
- `application/` — `ImportContentUseCase`, `QueryQuestionsUseCase`, `GetQuestionDetailUseCase`, `ManageQuestionsUseCase`, `CatalogQueryUseCase`, `ModuleDifficultyDefaults`, `SearchText`, `ContentImportException`

**`kg-core` — `com.knowledgegym.shared`** (dùng chung giữa các context):
- `application/` — `NotFoundException` (→404), `ConflictException` (→409)
- `domain/model/` — `PageResult` (pagination trung lập), `Difficulty`, `UserRole`, `NoteType`, `BlogStatus`, `AgentType`, `BaseEntity`

**`kg-infrastructure`**:
- `content/` — `JsoupContentSource` (implements `ContentSource`, parse `.qa-card`, `CompletableFuture` song song), `JsoupAnswerHtmlSanitizer` (Jsoup `Safelist`, loại script/iframe/`speak-notes`/`flow-diagram`), `ContentImportJobService` (import async → HTTP 202 + in-memory job registry, max 20 job)
- `persistence/adapter/` — `QuestionRepositoryAdapter` (native `ON CONFLICT (module_id, sort_order) DO UPDATE`), `TopicRepositoryAdapter`, `ModuleRepositoryAdapter`
- `persistence/dao/` — `QuestionSearchDao` (native tsquery + `ts_rank`)
- `config/` — `CacheConfig` (Caffeine), `AppContentProperties` (`app.content.*`), `UseCaseConfig` (bean cho content use case + `contentImportExecutor`)

**`kg-presentation`**:
- `rest/content/` — `QuestionController`, `QuestionDetailController`, `TopicController`, `ModuleController`, `AdminContentController`
- `rest/content/dto/` — `QuestionSummaryDTO`, `QuestionDetailDTO`, `QuestionOptionDTO`, `AdminQuestionDTO`, `TopicDTO`, `ModuleDTO`, `PageResponse`
- `config/` — `CachingConfig` (`@EnableCaching`), `OpenApiConfig` (Swagger/springdoc, title "Knowledge Gym API")
- `advice/` — `GlobalExceptionHandler` (thêm `ConflictException`→409, `ContentImportException`→500)

---

## Kiến trúc: Clean Architecture + DDD (Hexagonal / Ports & Adapters)

**Build tool: Gradle (Kotlin DSL)** — `build.gradle.kts`, Gradle 8.x, Java 21 toolchain.
Lưu ý: Kotlin DSL chỉ là cú pháp viết **file build script của Gradle** — toàn bộ code ứng dụng vẫn là **Java 21 + Spring Boot**, không có `.kt` trong `src/`.

### Dependency Rule (Clean Architecture — phụ thuộc hướng vào)

**Gradle modules (4):** `kg-core` · `kg-infrastructure` · `kg-presentation` · `kg-agent`

```
kg-presentation ──▶ kg-core (packages: application + domain) ◀── kg-infrastructure
                         ▲
                    kg-agent (bounded module, cùng JVM)
```

Trong **`kg-core`** (1 module):
- package **`…/domain/`** — thuần Java, không Spring/JPA; Aggregates, VOs, Domain Services, port interfaces
- package **`…/application/`** — UseCases; có thể dùng `@Service`/`@Transactional` (Spring là detail của tầng này)

- **`kg-infrastructure`**: adapter ngoài — JPA entities + Spring Data, JWT, Redis/Caffeine, Jsoup, Resilience4j. Implement port của domain.
- **`kg-presentation`**: Controllers, DTO/Mapper, filters, `@ControllerAdvice`. Chỉ gọi UseCase.
- **`kg-agent`**: collector/writer scheduling — ADR-001 modular monolith, không microservice.

> Tài liệu cũ đôi khi viết `kg-domain` / `kg-application` — đó là **tầng logic**, không phải tên Gradle module. Cook theo **`kg-core`**.

**ArchUnit test** enforce: presentation không import infrastructure.persistence; domain package không import Spring/JPA.

### DDD Bounded Contexts (package theo feature, không theo layer)

Mỗi context chứa đủ 4 tầng (vertical slice):

```
com.knowledgegym
├── identity/          # Context: Auth, User, RBAC          (Module 15)
├── content/           # Context: Question, Topic, Parser     (Module 01, 06)
├── learning/          # Context: SRS, Quiz, Mock Interview   (Module 11, 13)
├── progress/          # Context: Mastery, Streak, Dashboard  (Module 04)
├── notes/             # Context: Notes, Bookmark, Export     (Module 06)
├── blog/              # Context: Agent, Collector, Writer    (Module 07)
└── shared/            # Cross-cutting: audit, events, common
```

Mỗi context:
```
learning/
├── domain/            # SRSCard aggregate, Sm2Scheduler domain service, port interfaces
├── application/       # ReviewCardUseCase, GenerateQuizUseCase (Strategy pattern)
├── infrastructure/    # JpaSRSCardRepository (impl port), RedisLeaderboardAdapter
└── presentation/      # SRSController, QuizController, DTOs
```

---

## Gradle Multi-Module (Kotlin DSL)

```
knowledge-gym/
├── settings.gradle.kts                     # rootProject.name + include các module
├── build.gradle.kts                        # root: plugin mgmt, Java 21 toolchain, Spring BOM
├── gradle/
│   ├── libs.versions.toml                  # version catalog (Spring Boot 3.2, deps tập trung)
│   └── wrapper/gradle-wrapper.properties   # Gradle 8.x wrapper
├── gradlew, gradlew.bat
│
├── kg-core/                                # DOMAIN + APPLICATION (2 packages, 1 Gradle module)
│   ├── build.gradle.kts                    # deps: java only (domain) + spring-tx/context (application)
│   └── src/main/java/com/knowledgegym/
│       ├── shared/
│       │   ├── domain/                     # thuần Java — KHÔNG spring/jpa annotation
│       │   │   ├── model/                  # (m4a) PageResult, Difficulty, enums, BaseEntity
│       │   │   └── port/                   # Repository interface, EventBus port, Clock port
│       │   └── application/
│       │       ├── NotFoundException.java, ConflictException.java   # (m4a) → 404 / 409
│       │       ├── event/                  # DomainEvent, ProgressUpdatedEvent
│       │       └── common/                 # UseCase marker, BusinessException
│       │
│       ├── identity/
│       │   ├── domain/
│       │   │   ├── model/User.java         # Aggregate Root (plain Java, @Override equals/hashCode)
│       │   │   ├── model/UserRole.java
│       │   │   └── port/UserRepository.java, PasswordHasher.java, TokenService.java
│       │   └── application/
│       │       ├── RegisterUseCase.java
│       │       ├── LoginUseCase.java
│       │       └── RefreshTokenUseCase.java
│       │
│       ├── content/                        # (m4a) Context: Question, Topic, Parser
│       │   ├── domain/model/Question.java, QuestionOption.java, Topic.java, ModuleRef.java,
│       │   │                TopicWithStats.java, ModuleWithStats.java, ContentCatalog.java,
│       │   │                ParsedQuestion.java, QuestionQuery.java
│       │   ├── domain/port/QuestionRepository.java, TopicRepository.java,
│       │   │               ModuleRepository.java, ContentSource.java, AnswerHtmlSanitizer.java
│       │   └── application/
│       │       ├── ImportContentUseCase.java
│       │       ├── QueryQuestionsUseCase.java     # pagination, filter, full-text
│       │       ├── GetQuestionDetailUseCase.java
│       │       ├── ManageQuestionsUseCase.java    # admin CRUD, sanitize + searchKeywords
│       │       ├── CatalogQueryUseCase.java       # topics/modules + stats
│       │       ├── SearchText.java                # tokenizer (bỏ dấu, n-gram, synonym Việt–Anh)
│       │       ├── ModuleDifficultyDefaults.java
│       │       └── ContentImportException.java
│       │
│       ├── learning/
│       │   ├── domain/
│       │   │   ├── model/SRSCard.java      # Aggregate Root
│       │   │   ├── model/QuizSession.java, QuizAnswer.java
│       │   │   ├── model/InterviewSession.java
│       │   │   ├── service/Sm2Scheduler.java   # Domain Service — SM-2 algorithm
│       │   │   └── port/SRSCardRepository.java, QuizSessionRepository.java
│       │   └── application/
│       │       ├── EnrollCardsUseCase.java
│       │       ├── ReviewCardUseCase.java
│       │       ├── GenerateQuizUseCase.java
│       │       ├── strategy/QuizGenerationStrategy.java   # Strategy pattern (Module 13)
│       │       │   ├── RandomQuizStrategy.java
│       │       │   ├── WeaknessFocusedStrategy.java
│       │       │   ├── InterviewSimulatorStrategy.java
│       │       │   └── SpacedRepetitionStrategy.java
│       │       └── SubmitMockAnswerUseCase.java           # keyword grading
│       │
│       ├── progress/
│       │   ├── domain/model/UserProgress.java
│       │   ├── domain/service/MasteryCalculator.java, StreakCalculator.java
│       │   ├── domain/port/UserProgressRepository.java, LeaderboardPort.java
│       │   └── application/
│       │       ├── RecordAttemptUseCase.java      # đồng bộ, cùng tx với insert attempt (m7)
│       │       ├── QueryProgressUseCase.java      # radar + streak + XP
│       │       ├── QueryHeatmapUseCase.java       # 90 ngày, bucket theo timezone
│       │       └── QueryLeaderboardUseCase.java   # cache-aside lb:global
│       │
│       │   # Lưu ý: StudyAttempt model/port + StudyAttemptRepository nằm ở learning/, KHÔNG lặp lại ở đây.
│       │   # m7 KHÔNG có ProgressUpdatedEvent/async listener: XP materialize cùng tx + TTL 1h là đủ.
│       │
│       ├── notes/
│       │   ├── domain/model/Note.java, NoteType.java
│       │   ├── domain/port/NoteRepository.java
│       │   └── application/SaveNoteUseCase.java, SearchNotesUseCase.java, ConvertToCardUseCase.java
│       │
│       └── blog/
│           ├── domain/
│           │   ├── model/BlogPost.java, BlogStatus.java   # State pattern
│           │   ├── model/CollectedItem.java, CollectorSource.java, AgentRun.java
│           │   ├── port/CollectorPort.java, AiWriterPort.java, BlogPostRepository.java
│           │   └── service/QualityScorer.java             # Domain Service
│           └── application/
│               ├── CollectItemsUseCase.java
│               ├── SelectTopicUseCase.java               # 2 strategies MVP
│               ├── GenerateBlogUseCase.java
│               ├── template/BlogTemplate.java            # Template Method (Module 13)
│               │   ├── DeepDiveTemplate.java
│               │   └── ComparisonTemplate.java
│               └── ApproveBlogUseCase.java
│
├── kg-infrastructure/                      # ADAPTERS OUT (implement port của kg-core)
│   ├── build.gradle.kts                    # spring-boot-starter-data-jpa, security, redis, jjwt, jsoup, rome, resilience4j
│   └── src/main/java/com/knowledgegym/infrastructure/
│   │   ├── persistence/                    # JPA entities (khác domain model!) + Spring Data + adapter
│   │   │   ├── entity/UserJpaEntity.java, QuestionJpaEntity.java, SrsCardJpaEntity.java, SrsDeckJpaEntity.java, StudyAttemptJpaEntity.java, ...
│   │   │   ├── repository/SpringDataUserRepository.java, ...   # extends JpaRepository
│   │   │   └── adapter/UserRepositoryAdapter.java              # implements domain port, map Entity ↔ Domain
│   │   │       ├── QuestionRepositoryAdapter.java
│   │   │       ├── SRSCardRepositoryAdapter.java
│   │   │       └── mapper/JpaMapper.java                        # Adapter pattern (Module 13)
│   │   ├── security/                       # (m3 đã có) JWT, cookie, OAuth2, rate limit, ClientIpResolver
│   │   │   ├── JwtTokenService.java        # implements TokenService port
│   │   │   ├── BCryptPasswordHasher.java   # implements PasswordHasher port
│   │   │   ├── JwtAuthenticationFilter.java
│   │   │   ├── OAuth2SuccessHandler.java
│   │   │   ├── RateLimitFilter.java        # Bucket4j Redis
│   │   │   ├── ClientIpResolver.java       # XFF chỉ khi trust-forwarded-headers
│   │   │   ├── RefreshTokenCookie.java
│   │   │   └── RedisRefreshTokenCacheAdapter.java
│   │   ├── email/GmailEmailService.java    # (m3) implements EmailPort
│   │   ├── content/                        # (m4a) JsoupContentSource, JsoupAnswerHtmlSanitizer,
│   │   │                                   #       ContentImportJobService (async import + job registry)
│   │   ├── cache/CaffeineQuestionCache.java, RedisLeaderboardAdapter.java   # m5+ (m4a dùng config/CacheConfig)
│   │   ├── ai/OpenAiWriterAdapter.java      # implements AiWriterPort (GPT-4o-mini, @CircuitBreaker)
│   │   ├── collector/RssCollectorAdapter.java, GitHubTrendingAdapter.java   # implements CollectorPort
│   │   ├── export/PdfExporter.java, MarkdownExporter.java
│   │   └── config/SecurityConfig.java, CacheConfig.java, AsyncConfig.java,
│   │               SchedulingConfig.java, AppContentProperties.java, UseCaseConfig.java, ArchTestConfig.java
│   └── src/main/resources/db/migration/     # Flyway V001–V015 (m4a: V014 import support, V015 FTS)
│
├── kg-presentation/                         # ADAPTERS IN (driving adapters)
│   ├── build.gradle.kts                    # spring-boot-starter-web
│   └── src/main/java/com/knowledgegym/presentation/
│   │   ├── KnowledgeGymApplication.java    # @SpringBootApplication + @EnableAsync/@EnableScheduling
│   │   ├── rest/
│   │   │   ├── auth/                       # (m3) AuthController + request/response records
│   │   │   ├── content/                    # (m4a) QuestionController, QuestionDetailController,
│   │   │   │                               #       TopicController, ModuleController, AdminContentController
│   │   │   │   └── dto/                    # (m4a) QuestionSummaryDTO, QuestionDetailDTO, QuestionOptionDTO,
│   │   │   │                               #       AdminQuestionDTO, TopicDTO, ModuleDTO, PageResponse
│   │   │   ├── quiz/                       # (m6) QuizController
│   │   │   ├── interview/                  # (m6) MockInterviewController
│   │   │   ├── srs/                        # (m5) SRSController + dto/
│   │   │   ├── dashboard/                  # (m7) DashboardController + dto/DashboardResponses
│   │   │   └── mapper/UseCaseMapper.java   # DTO ↔ UseCase Command/Query
│   │   ├── config/                         # (m4a) CachingConfig (@EnableCaching), OpenApiConfig (springdoc)
│   │   ├── advice/GlobalExceptionHandler.java   # @RestControllerAdvice + RFC 7807 (m3, mở rộng m4a)
│   │   └── websocket/ProgressWebSocketHandler.java   # nếu build, không thì REST polling
│   └── src/test/java/                      # ArchUnit + AuthIntegrationTest (Testcontainers)
│       ├── PresentationLayerArchTest.java
│       └── (kg-core) DomainLayerArchTest.java
│
├── kg-agent/                               # Blog Agent — module Gradle riêng, compose cùng app (modular monolith, ADR-001)
│   ├── build.gradle.kts                    # depends on kg-core + kg-infrastructure
│   └── src/main/java/com/knowledgegym/agent/
│       ├── CollectorScheduler.java         # @Scheduled */6h → CollectItemsUseCase
│       ├── WriterScheduler.java            # queue → GenerateBlogUseCase
│       └── AgentAdminController.java       # trigger + stats
│
├── kg-frontend/                            # Next.js (npm, không nằm trong Gradle build)
│   ├── package.json
│   ├── app/
│   │   ├── (auth)/login/page.tsx, register/page.tsx
│   │   ├── page.tsx                        # Redirect → /questions (KHÔNG phải Dashboard)
│   │   ├── dashboard/page.tsx              # (m7) radar + heatmap + leaderboard
│   │   ├── flashcard/[moduleId]/page.tsx
│   │   ├── quiz/[moduleId]/page.tsx
│   │   ├── mock-interview/page.tsx
│   │   ├── notes/page.tsx
│   │   ├── blog/page.tsx, blog/[slug]/page.tsx
│   │   └── admin/...
│   └── components/
│       ├── FlashcardDeck.tsx, QuizQuestion.tsx, MindmapGraph.tsx,
│       ├── KnowledgeRadar.tsx, HeatmapCalendar.tsx, MarkdownEditor.tsx
│       └── api-client.ts                   # axios wrapper, httpOnly cookie refresh
│
├── docker-compose.yml
├── Dockerfile                              # multi-stage: gradle build → JRE 21 slim
└── .github/workflows/ci.yml                # ./gradlew verify → docker build → push → deploy
```

---

## Gradle build files (Kotlin DSL)

**`settings.gradle.kts`**

```kotlin
rootProject.name = "knowledge-gym"

include(
    "kg-core",
    "kg-infrastructure",
    "kg-presentation",
    "kg-agent"
)
```

**`build.gradle.kts` (root)**

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.2.5" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
}

allprojects {
    group = "com.knowledgegym"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")   // required cho Spring MVC path variable
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
```

**`kg-core/build.gradle.kts`** — domain thuần Java, không Spring plugin:

```kotlin
plugins {
    java
}

dependencies {
    // application layer cần spring context/tx annotation; domain KHÔNG import chúng (ArchUnit enforce)
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework:spring-tx")
    compileOnly("org.springframework:spring-boot-starter-data-jpa")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
```

**`kg-infrastructure/build.gradle.kts`**

```kotlin
plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":kg-core"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    runtimeOnly("org.postgresql:postgresql")

    implementation("com.github.jwacker:jjwt-api:0.12.5")
    runtimeOnly("com.github.jwacker:jjwt-impl")
    runtimeOnly("com.github.jwacker:jjwt-jackson")

    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.rometools:rome:2.1.0")
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("com.bucket4j:bucket4j-core:8.10.1")
    implementation("org.flywaydb:flyway-core")
}
```

**`kg-presentation/build.gradle.kts`**

```kotlin
plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":kg-core"))
    implementation(project(":kg-infrastructure"))   // composition root wiring
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")

    testImplementation("com.tngtech.archunit:archunit-junit5:1.2.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}
```

**Commands:** `./gradlew build` · `./gradlew bootRun` · `./gradlew test` · `./gradlew dependencies` (soi dependency graph)

---

## Tại sao chọn kiến trúc này

| Khái niệm | Học từ module docs/ | Áp dụng ở đâu |
|-----------|---------------------|----------------|
| Aggregate Root, Value Object | 12 (Software Design) | `SRSCard`, `User`, `Note` |
| Port & Adapter / Hexagonal | 12, 13 (Adapter pattern) | `domain/port/*` vs `infrastructure/adapter/*` |
| Strategy pattern | 13 | `QuizGenerationStrategy` |
| Template Method | 13 | `BlogTemplate` |
| State pattern | 13 | `BlogPost.status` |
| @TransactionalEventListener | 02, 03 | ~~`RecordAttemptUseCase`~~ — **m7 không dùng**: XP/`user_progress` ghi **đồng bộ cùng tx**; Redis chỉ có TTL, không evict |
| CircuitBreaker/Retry | 07 | `OpenAiWriterAdapter` |
| ArchUnit | 12 | `ArchitectureTest` enforce dependency rule |
| Records (DTO) | 01 | `presentation/rest/dto` |

## Dependency direction (kiểm chứng bằng ArchUnit)

```
presentation ──▶ application ──▶ domain ◀── infrastructure
     ✗ không import infrastructure.persistence
     ✗ domain không import org.springframework / jakarta.persistence
     ✓ infrastructure implements domain.port
```
