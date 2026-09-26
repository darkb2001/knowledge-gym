# Project Structure — Knowledge Gym

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
│       │   │   ├── model/                  # Value Objects, enums, base entity ids
│       │   │   └── port/                   # Repository interface, EventBus port, Clock port
│       │   └── application/
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
│       ├── content/
│       │   ├── domain/model/Question.java, QuestionOption.java, Topic.java, ModuleRef.java
│       │   ├── domain/port/QuestionRepository.java, ContentSource.java
│       │   └── application/
│       │       ├── ImportContentUseCase.java
│       │       └── QueryQuestionsUseCase.java   # pagination, filter
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
│       │   ├── domain/service/MasteryCalculator.java, StreakCalculator.java
│       │   ├── domain/port/UserProgressRepository.java, StudyAttemptRepository.java
│       │   └── application/
│       │       ├── RecordAttemptUseCase.java              # @TransactionalEventListener AFTER_COMMIT
│       │       └── QueryDashboardUseCase.java             # radar, heatmap, leaderboard, streak
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
│   │   │   ├── entity/UserJpaEntity.java, QuestionJpaEntity.java, SRSCardJpaEntity.java, ...
│   │   │   ├── repository/SpringDataUserRepository.java, ...   # extends JpaRepository
│   │   │   └── adapter/UserRepositoryAdapter.java              # implements domain port, map Entity ↔ Domain
│   │   │       ├── QuestionRepositoryAdapter.java
│   │   │       ├── SRSCardRepositoryAdapter.java
│   │   │       └── mapper/JpaMapper.java                        # Adapter pattern (Module 13)
│   │   ├── security/
│   │   │   ├── JwtTokenProvider.java        # implements TokenService port
│   │   │   ├── BCryptPasswordHasher.java    # implements PasswordHasher port
│   │   │   ├── JwtAuthenticationFilter.java
│   │   │   └── OAuth2GoogleUserService.java
│   │   ├── ratelimit/RateLimitFilter.java   # Bucket4j
│   │   ├── cache/CaffeineQuestionCache.java, RedisLeaderboardAdapter.java
│   │   ├── html/JsoupContentSource.java     # implements ContentSource port (Module 01)
│   │   ├── ai/OpenAiWriterAdapter.java      # implements AiWriterPort (GPT-4o-mini, @CircuitBreaker)
│   │   ├── collector/RssCollectorAdapter.java, GitHubTrendingAdapter.java   # implements CollectorPort
│   │   ├── export/PdfExporter.java, MarkdownExporter.java
│   │   └── config/SecurityConfig.java, CacheConfig.java, AsyncConfig.java,
│   │               SchedulingConfig.java, OpenApiConfig.java, ArchTestConfig.java
│   └── src/main/resources/db/migration/     # Flyway V001–V013 (32 bảng; V009 indexes, V010 mview)
│
├── kg-presentation/                         # ADAPTERS IN (driving adapters)
│   ├── build.gradle.kts                    # spring-boot-starter-web
│   └── src/main/java/com/knowledgegym/presentation/
│   │   ├── KnowledgeGymApplication.java    # @SpringBootApplication + @EnableAsync/@EnableScheduling
│   │   ├── rest/
│   │   │   ├── AuthController.java, QuestionController.java, QuizController.java,
│   │   │   │   SRSController.java, MockInterviewController.java, NoteController.java,
│   │   │   │   BlogController.java, DashboardController.java, AdminController.java
│   │   │   ├── dto/                        # request/response records (Module 01 Records)
│   │   │   └── mapper/UseCaseMapper.java   # DTO ↔ UseCase Command/Query
│   │   ├── advice/GlobalExceptionHandler.java   # @ControllerAdvice + RFC 7807
│   │   └── websocket/ProgressWebSocketHandler.java   # nếu build, không thì REST polling
│   └── src/test/java/                      # ArchUnit test: dependency rule enforcement
│       └── ArchitectureTest.java           # domain không import spring/jpa; presentation không import infrastructure.persistence
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
│   │   ├── page.tsx                        # Dashboard
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
| @TransactionalEventListener | 02, 03 | `RecordAttemptUseCase` |
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
