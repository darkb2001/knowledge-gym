# Feature ↔ Knowledge Mapping: Build = Học

## Nguyên tắc

```
Mỗi feature trong app = 1 cơ hội thực hành kiến thức trong docs/
Không dùng shortcut — dùng đúng pattern, đúng annotation, đúng kiến thức đã học
```

---

## Module 01: Java Core & Memory

| Feature | Kiến thức áp dụng |
|---------|-------------------|
| Content Parser (parse HTML → Question) | String, StringBuilder, regex, Records |
| Cache Layer | HashMap, equals/hashCode, Lombok |
| DTO / VO pattern | Records, Generics, Immutability |
| Exception handling | Custom exceptions, try-with-resources |
| Config objects | Enums, Builder, Generics |

```java
// Records + Generics + equals/hashCode
public record QuestionDTO(
    String id, String module, String title,
    String content, Difficulty difficulty, List<String> tags
) implements Comparable<QuestionDTO> {
    @Override
    public int compareTo(QuestionDTO other) {
        return this.difficulty.ordinal() - other.difficulty.ordinal();
    }
}
```

---

## Module 02: Multithreading & Concurrency

| Feature | Kiến thức áp dụng |
|---------|-------------------|
| Batch content import | ExecutorService, CompletableFuture |
| Blog Agent scheduling | ScheduledExecutorService |
| SRS review queue | DB composite-index query (không in-memory queue) — m5 |
| Real-time analytics | ConcurrentHashMap, AtomicLong |
| Rate limiter cho AI API | Semaphore |
| WebSocket live progress | CompletableFuture + async |

```java
// Parallel content parsing
@Async
public CompletableFuture<List<Question>> parseAllModules(Path docsDir) {
    List<CompletableFuture<List<Question>>> futures = 
        Files.list(docsDir)
            .filter(p -> p.toString().endsWith(".html"))
            .map(file -> CompletableFuture.supplyAsync(
                () -> parseModule(file), executor
            ))
            .toList();
    
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .thenApply(v -> futures.stream()
            .map(CompletableFuture::join)
            .flatMap(Collection::stream).toList()
        );
}
```

---

## Module 03: Spring Boot & IoC

| Feature | Kiến thức áp dụng |
|---------|-------------------|
| Toàn bộ architecture | @RestController, @Service, @Repository |
| DI | Constructor injection, @Qualifier, profiles |
| Bean lifecycle | @PostConstruct, @PreDestroy |
| Config | @Configuration, @ConfigurationProperties |
| AOP logging | @Aspect, @Around cho audit |
| Transaction | @Transactional trên Service layer |
| Validation | @Valid, custom Validator |
| Scheduling | @Scheduled cho blog agent |
| Cache | @Cacheable |
| Async | @Async |

```java
// AOP audit logging
@Aspect
@Component
public class AuditAspect {
    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint jp, Auditable auditable) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = jp.proceed();
        long duration = System.currentTimeMillis() - start;
        auditRepo.save(new AuditLog(jp.getSignature().getName(), duration, ...));
        return result;
    }
}
```

---

## Module 04: JPA / Hibernate

| Feature | Kiến thức áp dụng |
|---------|-------------------|
| Entity mapping | @Entity, @Table, @Column |
| User ↔ Progress | @OneToMany, @ManyToOne |
| N+1 problem | @EntityGraph, JOIN FETCH |
| Pagination | Pageable, Specification |
| Auditing | @CreatedDate, @LastModifiedDate |
| Soft delete | @Where, @SQLDelete |
| Locking | @Version (optimistic lock) |
| Custom queries | @Query, JPQL, Native SQL |

---

## Module 05: Database & SQL

| Feature | Kiến thức áp dụng |
|---------|-------------------|
| Schema design | Normalization, indexes, constraints |
| Flyway migrations | Version-controlled schema |
| Complex queries | Window functions, CTE cho analytics |
| Performance | EXPLAIN ANALYZE, index optimization |
| MATERIALIZED VIEW | Pre-computed analytics |

---

## Module 06: REST API & Security

| Feature | Kiến thức áp dụng |
|---------|-------------------|
| RESTful design | Resource naming, HTTP methods, status codes |
| API versioning | URI versioning /api/v1/ |
| Pagination + Sorting | ?page=0&size=20&sort=createdAt,desc |
| Error handling | @ControllerAdvice, RFC 7807 |
| JWT auth | Access + Refresh token |
| RBAC | USER, ADMIN roles (MVP) — `PREMIUM` reserved trong enum, chưa dùng (monetization deferred) |
| Rate limiting | Bucket4j + Redis |
| Swagger | SpringDoc OpenAPI |

---

## Module 07: Microservices

| Feature | Kiến thức áp dụng |
|---------|-------------------|
| Blog Agent tách service | Service decomposition |
| Async communication | Events cho blog generation |
| Circuit breaker | Resilience4j khi call AI API |
| Rate limiter | Resilience4j RateLimiter |

```java
@CircuitBreaker(name = "openai", fallbackMethod = "fallbackGrade")
@Retry(name = "openai")
@RateLimiter(name = "openai")
public GradingResult gradeAnswer(String question, String userAnswer) {
    return openAiClient.chatCompletion(buildGradingPrompt(question, userAnswer));
}
```

---

## Module 09: Cloud & CI/CD

| Feature | Kiến thức áp dụng |
|---------|-------------------|
| Docker | Dockerfile cho Spring Boot |
| Docker Compose | App + PostgreSQL + Redis |
| GitHub Actions | CI: test → build → push |
| Monitoring | Prometheus + Grafana |
| Logging | ELK/Loki |

---

## Module 11: DSA

| Feature | Kiến thức áp dụng |
|---------|-------------------|
| SRS scheduling | Composite-index range query (B-tree) — không dùng in-memory heap |
| Content search | Trie / Full-text |
| Knowledge graph | Graph traversal (BFS/DFS) |
| Quiz randomization | Fisher-Yates shuffle |
| Dedup algorithm | Hash-based dedup |

```java
// SRS — SM-2 scheduling (đã ship ở m5: knowledge-gym/kg-core/.../learning/domain/service/Sm2Scheduler.java)
// LƯU Ý: thang quality của API là 0–3 (Again/Hard/Good/Easy), KHÔNG phải thang SM-2 gốc 0–5.
// Bảng canonical nằm ở plans/knowledge-gym/mini-phase-05-srs.md — snippet này chỉ minh hoạ.
public final class Sm2Scheduler {
    public static Schedule next(int quality, int repetitions, int intervalDays,
                                double easeFactor, LocalDate today) {
        int newRepetitions;
        int newIntervalDays;
        double newEaseFactor = easeFactor;

        switch (quality) {
            case 0 -> { // Again — quên hoàn toàn
                newRepetitions = 0;
                newIntervalDays = 1;
                newEaseFactor = clampEase(easeFactor - 0.2);
            }
            case 1 -> { // Hard — nhớ nhưng khó
                newRepetitions = repetitions + 1;
                newIntervalDays = repetitions == 0 ? 1 : ceil(intervalDays * 1.2);
                newEaseFactor = clampEase(easeFactor - 0.14);
            }
            case 3 -> { // Easy — quá dễ (bonus +30% interval, +0.1 ease)
                newRepetitions = repetitions + 1;
                newIntervalDays = repetitions == 0 ? 4 : ceil(intervalDays * easeFactor * 1.3);
                newEaseFactor = clampEase(easeFactor + 0.1);
            }
            default -> { // 2 = Good — giữ nguyên ease
                newRepetitions = repetitions + 1;
                newIntervalDays = repetitions == 0 ? 1 : ceil(intervalDays * easeFactor);
            }
        }
        return new Schedule(newIntervalDays, round2(newEaseFactor), newRepetitions,
                today.plusDays(newIntervalDays));
    }
}
```

Queue thẻ đến hạn **không** giữ in-memory (teaching snippet cũ dùng `PriorityQueue`): m5 query DB
`WHERE user_id = ? AND next_review <= today` theo composite index `idx_srs_due (user_id, next_review)`
— dữ liệu thẻ phải sống qua restart và nhiều instance.

---

## Module 13: Design Patterns

| Feature | Pattern | Code location |
|---------|---------|---------------|
| Quiz generators | **Strategy** | QuizGenerationStrategy |
| Content parsers | **Factory** | HtmlParserFactory |
| SRS algorithm | **Domain Service** (static, pure) | Sm2Scheduler |
| Event system | **Observer** | ApplicationEvent |
| API responses | **Builder** | ApiResponse.builder() |
| Blog templates | **Template Method** | BlogTemplate → DeepDive |
| Notification | **Chain of Responsibility** | Email→Push→Slack |
| State machine | **State** | BlogPost: DRAFT→REVIEW→PUBLISHED |
| DTO mapping | **Adapter** | Entity ↔ DTO mappers |

```java
// Teaching example only — KHÔNG phải contract production.
// m6: QuizGenerationStrategy.rank() ở application/strategy; Map<QuizStrategy,...>
// wiring tại UseCaseConfig, shuffle thuần Java tại domain/service/RandomOrder.
// Strategy pattern cho quiz
@Component
@Qualifier("random")
public class RandomQuizStrategy implements QuizGenerationStrategy {
    public List<Question> generate(Module module, int count, Difficulty diff) {
        return questionRepo.findRandomByModuleAndDifficulty(module, diff, count);
    }
}

@Component
@Qualifier("weakness")
public class WeaknessFocusedStrategy implements QuizGenerationStrategy {
    public List<Question> generate(Module module, int count, Difficulty diff) {
        return questionRepo.findWeakestTopics(currentUser, module, count);
    }
}
```

---

## Module 15: Auth / RBAC / OAuth2

| Feature | Kiến thức áp dụng |
|---------|-------------------|
| User registration | BCrypt password hashing |
| Login | JWT access + refresh token |
| OAuth2 login | **Google** sign-in (MVP). GitHub = deferred, `GITHUB` reserved trong `auth_provider` |
| RBAC | ROLE_USER, ROLE_ADMIN (MVP) — ROLE_PREMIUM reserved |
| Method security | @PreAuthorize, @Secured |
| Resource server | Spring Security Resource Server |

---

## Coverage Summary

| Module | Features touching it | Coverage |
|--------|---------------------|----------|
| 01 Java Core | Parser, DTO, Records, Collections | **100%** |
| 02 Multithreading | Async parsing, WebSocket, Semaphore | **100%** |
| 03 Spring Boot | DI, AOP, Scheduling, Caching, Validation | **100%** |
| 04 JPA/Hibernate | Entity, N+1, Pagination, Auditing, Locking | **100%** |
| 05 Database | Schema, Indexes, Migrations, MVIEW | **100%** |
| 06 REST API | CRUD, Pagination, Error handling, Swagger | **100%** |
| 07 Microservices | Circuit Breaker, Events | **80%** |
| 09 Cloud/CI/CD | Docker, Compose, GitHub Actions, Prometheus | **100%** |
| 11 DSA | Composite-index query, Graph, Fisher-Yates | **90%** |
| 12 Software Design | UML, ADR, SOLID | **80%** |
| 13 Design Patterns | Strategy, Factory, Observer, Builder, Template | **100%** |
| 15 Auth/RBAC/OAuth | JWT, OAuth2, RBAC, Resource Server | **100%** |
