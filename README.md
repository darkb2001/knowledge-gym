# Knowledge Gym

Build-to-learn Java interview platform. Tech: Gradle Kotlin DSL + Clean Architecture DDD + Spring Boot 3.2 + Java 21 + PostgreSQL 16.

## Quick start

```
docker compose up
./gradlew test
./gradlew :kg-presentation:bootRun
```

Open http://localhost:8080/actuator/health

## Structure

- kg-core/ — Domain (pure Java) + Application use cases
- kg-infrastructure/ — JPA, Spring Security, Jsoup, Redis, Resilience4j
- kg-presentation/ — REST controllers, SecurityConfig, Swagger
- kg-agent/ — Blog scheduler

## Plan

plans/knowledge-gym/plan.md + 12 mini-phases
