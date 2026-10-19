# Audit nội dung Java interview — 20 chủ đề

Đã đọc body của 529 card, không chỉ title. Append 10 card vào 9 module; không sửa/reorder card cũ.

| Chủ đề | Mapping | Kết quả |
|---|---|---|
|HashMap / Hashtable / CHM|01-java-core Q7,Q8; 02-multithreading Q12|Đã có|
|G1/ZGC và OOM|01-java-core Q6 + mới sort 23; 08-system-design Q16|Bổ sung runbook|
|Runnable / Callable / CompletableFuture|02-multithreading Q2,Q9|Đã có|
|Batch DB / CPU / backpressure / Hikari|02-multithreading Q8 + mới sort 20; 05-database Q37|Bổ sung sizing|
|Java 8–21|01-java-core Q19 (sort 17)|Đã có, có vài chú thích lịch sử cần đọc với version cụ thể|
|Bean lifecycle/scopes/ObjectProvider|03-spring-boot Q3 + mới sort 26|Bổ sung prototype-in-singleton|
|AOP JDK/CGLIB / audit|03-spring-boot Q9,D1 + mới sort 27|Bổ sung audit safe + đính chính JDK final|
|REQUIRED / REQUIRES_NEW / private / self|03-spring-boot Q12,D2; mới sort 27; 38-elasticsearch mới sort 11|Đã có; bổ sung cảnh báo outbox không REQUIRES_NEW|
|Filter / Interceptor / JWT|15-auth-rbac-oauth mới sort 15|Bổ sung boundary|
|N+1|04-jpa-hibernate Q2; 41-sql-performance-tuning Q6|Đã có fetch/entitygraph/batch/pagination caveats|
|L1/L2 stale cache|04-jpa-hibernate Q3|Đã có|
|Phantom PG/Oracle|05-database Q11,Q12–Q15,Q16 + mới sort 47|Bổ sung đính chính PG RR|
|SQL tuning / DEM DET / plans / bitmap / partition|41-sql-performance-tuning Q1–Q10 + mới sort 11; 05-database Q26|Bổ sung actual Oracle + bitmap; DEM/DET chưa có schema thật|
|Redis use cases / penetration / breakdown / avalanche|08-system-design Q5,Q6; 05-database D3|Đã có|
|Hash/ZSET / rate limiting|08-system-design mới sort 21|Bổ sung|
|Inverted index / LIKE|38-elasticsearch Q1|Đã có|
|DB → ES/Redis outbox/CDC|38-elasticsearch Q9 + mới sort 11; 07-microservices Q16|Bổ sung consistency limits|
|Monolith extraction / banking saga|07-microservices Q1,Q14,Q15,Q23 + mới sort 26|Bổ sung ví dụ giả định rõ|
|Strategy example|13-design-patterns Q9|Đã có FIFO/FEFO/nearest-bin|
|OSGi lifecycle/dependency/classloaders|39-modular-architecture-osgi Q4–Q7|Đã có|

## Production

- Import job `5370822e-3c78-4941-af7d-9a57a309b8e1`: SUCCEEDED, topics=15, modules=38, questions=539, deleted=0, orphanModuleSlugs=[].
- DB: 529→539 questions; 2116→2156 options; 4 options và 1 correct cho mỗi câu; 10 card mới options_manual=true.
- 529 question IDs/module IDs/sort_order giữ nguyên. Notes JSON không thay đổi (baseline 0 notes).
- Authenticated GET /questions/{id}: 200 cho cả 10 card mới, summary và diagram đọc lại từ DB.
- Không restart service, không thay model/config/infrastructure.
- Chưa kiểm UI authenticated bằng browser; xác minh DB và API.
- DEM/DET là tên chưa có schema: ví dụ master/detail được ghi rõ giả định, không bịa schema production.
- Backup: `/home/deploy/kg-interview-audit-backup-20261019-090248` (docs + pg_dump).

## Lưu ý bảo toàn

Các card cũ có một số phát biểu cần đọc cùng card bổ sung: PostgreSQL RR không thấy phantom từ TX khác; business outbox phải cùng transaction, không REQUIRES_NEW; final target method có thể advise qua JDK interface proxy. Giữ nguyên card cũ để không thay IDs/order; card mới giải thích trực tiếp.

## Nguồn

- https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshoot-memory-leaks.html
- https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing
- https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html
- https://docs.spring.io/spring-security/reference/servlet/architecture.html
- https://www.postgresql.org/docs/current/transaction-iso.html
- https://www.postgresql.org/docs/current/indexes-bitmap-scans.html
- https://docs.oracle.com/en/database/oracle/oracle-database/19/tgsql/generating-and-displaying-execution-plans.html
- https://redis.io/docs/latest/develop/data-types/sorted-sets/
- https://redis.io/docs/latest/develop/data-types/hashes/
- https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html
- https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- https://microservices.io/patterns/data/saga.html
