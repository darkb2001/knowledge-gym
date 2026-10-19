# Java interview coverage audit

## Verified production result

- Actual pre-import DB: 15 topics, 38 modules, 539 questions, 2156 options. Initial HTML inventory was 529; source subsequently contained the 10 existing production deepening cards. Latest 539-card source was hash-checked before writing.
- Added only 5 new cards. Final DB: 15 topics, 38 modules, 544 questions, 2176 options.
- Import job `6e0671e6-29da-43bb-865a-ef7d384bcd40`: SUCCEEDED; questions=544; deleted=0; orphanModuleSlugs=[].
- All 539 pre-existing question UUIDs, module/sort positions, titles and answer HTML unchanged on DB read-back.
- Notes: 0 before and after; complete row-set fingerprint unchanged. No service restart.
- All 544 questions have exactly four options and one correct answer. Five new questions have own-answer manual options, retaining generated option UUIDs.
- Authenticated GET /questions/{id} read-back verified all five new bodies; /tracks, /topics, /modules read successfully. UI rendering was not verified; no claim of browser QA.
- Backup: /home/deploy/kg-audit-backup/pre-additive.dump and docs-before on CT102.

## Topic-by-topic mapping

### 1. HashMap collision/treeification/Hashtable/CHM

- `01-java-core` #7: HashMap hoạt động như thế nào?
- `01-java-core` #9: HashMap vs ConcurrentHashMap
- `02-multithreading` #12: ConcurrentHashMap — CAS, compute(), merge()?

Existing full bodies cover collisions, thresholds, Hashtable and CHM; no duplicate added.

### 2. G1/ZGC OOM despite free RAM / production debugging

- `01-java-core` #6: Garbage Collection — Young Gen, Old Gen, G1GC, ZGC
- `01-java-core` #23: OOM dù máy còn RAM: G1/ZGC và runbook production
- `08-system-design` #16: Tình huống 3: High CPU / High Memory / OOM

Existing production deepening covers heap/native/cgroup and safe runbook; retained unchanged.

### 3. Runnable / Callable / CompletableFuture

- `02-multithreading` #2: Phân biệt Runnable vs Callable?
- `02-multithreading` #9: CompletableFuture — async pipeline?
- `02-multithreading` #21: CompletableFuture: timeout, cancellation và quan sát lỗi không đồng nghĩa dừng I/O

Added #21: timeout/cancel does not stop I/O, exception observation and stage execution semantics.

### 4. Bounded batch DB pool, CPU, backpressure, Hikari

- `02-multithreading` #8: ExecutorService & Thread Pool — cấu hình và rejection policy?
- `02-multithreading` #20: Batch DB hữu hạn: sizing CPU, Hikari và backpressure bằng số cụ thể
- `05-database` #29: HikariCP tuning?

Existing #20 already includes concrete budgets, shared-pool reservation caveat, finite producer and checkpoints.

### 5. Java 8–21 features

- `01-java-core` #16: Records & Sealed Classes
- `01-java-core` #17: So sánh Java 8, 11, 17, 21 — nên chọn bản nào?
- `01-java-core` #18: Streams & Lambda — Lazy evaluation
- `01-java-core` #24: Mốc Java 8–21: finalized, preview và sửa các nhầm lẫn migration

Added #24: finalized/preview timeline, var=10, records=16, switch patterns=21, Generational ZGC clarification.

### 6. Bean lifecycle, scopes, prototype in singleton, ObjectProvider

- `03-spring-boot` #3: Vòng đời Spring Bean (Bean Lifecycle)?
- `03-spring-boot` #26: Bean scopes: prototype trong singleton và ObjectProvider

Existing #26 covers provider per operation and prototype cleanup; no duplicate.

### 7. AOP JDK/CGLIB and safe audit logs

- `03-spring-boot` #9: AOP — Aspect, Join Point, Advice, Pointcut?
- `03-spring-boot` #23: Spring AOP tạo proxy thế nào — CGLIB vs JDK Dynamic Proxy, và vì sao final/private/self-invocation phá advice?
- `03-spring-boot` #27: AOP audit an toàn: allowlist log và phân biệt attempt với committed event

Existing #27 covers allowlist, redaction, final-method/JDK nuance and return vs commit.

### 8. Transactions REQUIRED / REQUIRES_NEW / private / self-invocation

- `03-spring-boot` #12: @Transactional — propagation, isolation, pitfalls?
- `03-spring-boot` #24: @Transactional rollback: mặc định khi nào rollback, checked exception có rollback không, rollbackFor/noRollbackFor và readOnly khác gì?
- `03-spring-boot` #27: AOP audit an toàn: allowlist log và phân biệt attempt với committed event
- `07-microservices` #16: Transactional Outbox Pattern — giải quyết Dual-Write?

Existing corrective #27 and outbox card explicitly require business/outbox in same transaction; old cards retained.

### 9. Filter vs Interceptor / JWT chain

- `15-auth-rbac-oauth` #5: Spring Security Filter Chain & SecurityContext — end-to-end?
- `15-auth-rbac-oauth` #15: Filter vs HandlerInterceptor: JWT nằm ở đâu trong request chain?

Existing #15 includes first matching security chain, JWT validation and dispatch caveats.

### 10. N+1 fetch join / EntityGraph / batch caveats

- `04-jpa-hibernate` #2: N+1 Query — nguyên nhân, phát hiện, giải pháp?
- `04-jpa-hibernate` #6: Pagination — Page vs Slice, ảnh hưởng COUNT(*)?
- `04-jpa-hibernate` #10: Đo hiệu năng ORM bằng Hibernate Statistics — phát hiện N+1 khách quan?
- `41-sql-performance-tuning` #6: N+1 query: nhận diện và sửa (JOIN FETCH, batch fetching, @EntityGraph)?

Existing bodies include cartesian product, multiple bags and two-query pagination; no duplicate.

### 11. L1/L2 stale cache

- `04-jpa-hibernate` #3: First-level cache vs Second-level cache?
- `04-jpa-hibernate` #11: Stale L1/L2 sau native SQL hoặc writer ngoài Hibernate: refresh không phải thần dược

Added #11: external/native writers, L1 vs L2 eviction, refresh/clear hazards and repeatable-read snapshot.

### 12. Isolation / phantom / PostgreSQL vs Oracle

- `05-database` #11: 4 Isolation Levels?
- `05-database` #13: Isolation Level ngăn phenomenon nào?
- `05-database` #15: Snapshot Isolation vs Serializable Snapshot Isolation (SSI)?
- `05-database` #47: Phantom: PostgreSQL REPEATABLE READ khác bảng SQL chuẩn và Oracle

Existing #47 corrects old PostgreSQL RR phantom claim and covers write skew, SSI and Oracle differences.

### 13. SQL DEM/DET, estimated/actual, B-tree, bitmap, partitions

- `41-sql-performance-tuning` #1: Đọc execution plan: PostgreSQL EXPLAIN (ANALYZE, BUFFERS) vs Oracle EXPLAIN PLAN?
- `41-sql-performance-tuning` #2: Index B-tree hoạt động thế nào, selectivity/cardinality là gì, khi nào planner bỏ index?
- `41-sql-performance-tuning` #8: Partitioning và partial index — khi nào hiệu quả, các loại partition?
- `41-sql-performance-tuning` #10: Khác biệt PostgreSQL ↔ Oracle khi viết SQL và tối ưu?
- `41-sql-performance-tuning` #11: SQL DEM/DET: actual plan Oracle, bitmap và kiểm chứng tối ưu

Existing #11 marks DEM/DET schema as hypothetical and covers DISPLAY_CURSOR, actual loops, Oracle bitmap vs PG scan.

### 14. Redis use cases / penetration / breakdown / avalanche

- `08-system-design` #5: Redis Caching — Cache-Aside, Write-Through, Write-Behind
- `08-system-design` #6: Cache Penetration, Breakdown, Avalanche
- `08-system-design` #22: Redis token bucket dùng Hash: đơn vị thời gian, lease owner và giới hạn atomicity
- `05-database` #46: Cache-aside, TTL và cache stampede — thiết kế cache tầng ứng dụng?

Added #22 includes Bloom filter false-positive correction, lock ownership and outage/load shedding caveats.

### 15. Hash vs ZSET rate limiting

- `08-system-design` #8: Traffic Spikes — Rate Limiting (Token Bucket / Leaky Bucket)
- `08-system-design` #21: Redis Hash vs ZSET và rate limit sliding window atomic
- `08-system-design` #22: Redis token bucket dùng Hash: đơn vị thời gian, lease owner và giới hạn atomicity

Existing #21 covers ZSET sliding log; added #22 deepens Hash token bucket units, atomicity and lease safety.

### 16. Inverted index vs LIKE

- `38-elasticsearch` #1: Inverted index là gì, vì sao ES tìm text nhanh hơn LIKE của SQL?
- `38-elasticsearch` #2: Mapping & dynamic mapping: text vs keyword, vì sao khai mapping tường minh?
- `38-elasticsearch` #3: Analysis pipeline: analyzer, tokenizer, filter và custom analyzer cho tiếng Việt?

Existing bodies cover postings/analyzers and substring limitations; no duplicate.

### 17. DB → Elasticsearch/Redis outbox/CDC consistency limits

- `07-microservices` #16: Transactional Outbox Pattern — giải quyết Dual-Write?
- `38-elasticsearch` #9: Đồng bộ DB → ES: dual write vs outbox/CDC, reindex alias swap, thứ tự sự kiện?
- `38-elasticsearch` #11: DB → ES/Redis: giới hạn consistency, version và chống stale resurrection

Existing #11 covers version/tombstone, stale cache resurrection, replay/catch-up and eventual consistency.

### 18. Monolith extraction / saga / distributed banking EXAMPLE

- `07-microservices` #1: Microservices vs Monolith?
- `07-microservices` #14: Saga Pattern — Choreography vs Orchestration + Compensating?
- `07-microservices` #15: Two-Phase Commit (2PC)?
- `07-microservices` #23: Phân rã service: Business Capability vs Bounded Context (DDD)?
- `07-microservices` #26: Ví dụ thiết kế ngân hàng: tách monolith và saga không giả mạo kinh nghiệm
- `08-system-design` #2: Thiết kế Payment System

Existing #26 explicitly hypothetical, local ACID ledger first, UNKNOWN timeout and reconciliation; no fabricated experience.

### 19. Design patterns / Strategy example

- `13-design-patterns` #9: Strategy Pattern
- `13-design-patterns` #21: Behavioral patterns: Strategy vs State vs Observer vs Template Method vs Chain of Responsibility

Existing concrete AllocationStrategy/FIFO/FEFO context and State comparison; no duplicate.

### 20. OSGi lifecycle / dependencies / classloaders

- `39-modular-architecture-osgi` #4: OSGi là gì và bundle lifecycle hoạt động thế nào?
- `39-modular-architecture-osgi` #5: Vì sao mỗi bundle có classloader riêng và hệ quả với static/singleton, ClassCastException?
- `39-modular-architecture-osgi` #6: OSGi services & Declarative Services: component, service registry, Config Admin, dependency động?
- `39-modular-architecture-osgi` #7: Versioning & Import/Export package: bundle A phụ thuộc B thì nâng cấp thế nào?
- `39-modular-architecture-osgi` #10: OSGi dependency động: static/dynamic reference và classloader không phải sandbox

Added #10: dynamic/optional DS semantics, shared defining classloader, wiring refresh and no sandbox claim.

## Scope and remaining cautions

Existing cards were intentionally not rewritten, including older overly broad statements; corrective deepening cards are mapped above. DEM/DET remains undefined by the request; existing content labels its example schema as hypothetical, not a discovered user schema. Production runtime behavior of illustrative Java/SQL snippets was not load-tested.

## Sources

- https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshooting-memory-leaks.html
- https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-garbage-collector-tuning.html
- https://openjdk.org/jeps/439
- https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html
- https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing
- https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html
- https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html
- https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html
- https://www.postgresql.org/docs/current/transaction-iso.html
- https://docs.oracle.com/en/database/oracle/oracle-database/19/cncpt/data-concurrency-and-consistency.html
- https://www.postgresql.org/docs/current/indexes-bitmap-scans.html
- https://docs.oracle.com/en/database/oracle/oracle-database/19/cncpt/indexes-and-index-organized-tables.html
- https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/DBMS_XPLAN.html
- https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/interceptors.html
- https://redis.io/docs/latest/develop/data-types/sorted-sets/
- https://redis.io/docs/latest/develop/programmability/eval-intro/
- https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html
- https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html
- https://openjdk.org/jeps/444
- https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/strangler-fig.html
- https://docs.osgi.org/specification/osgi.cmpn/8.1.0/service.component.html
- https://docs.osgi.org/specification/osgi.core/8.0.0/framework.module.html
- https://openjdk.org/jeps/286
- https://openjdk.org/jeps/441
- https://www.postgresql.org/docs/current/using-explain.html
- https://docs.spring.io/spring-security/reference/servlet/architecture.html
