plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

// Keep Tomcat pin consistent with kg-presentation (CVE-2026-65182).
extra["tomcat.version"] = "11.0.26"

dependencies {
    api(project(":kg-core"))

    // Spring Boot BOM — cung cấp version cho starters + flyway (single source: libs.versions.toml)
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))

    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.kafka)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.client)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.cache)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.spring.boot.starter.actuator)
    // servlet API cần cho JwtAuthenticationFilter, RateLimitFilter, OAuth2SuccessHandler
    implementation(libs.spring.boot.starter.web)
    implementation(libs.flyway.core)
    // Bắt buộc từ Flyway 10: hỗ trợ PostgreSQL tách khỏi flyway-core. Thiếu artifact này thì
    // Flyway 12 không nhận ra dialect và app fail lúc boot.
    implementation(libs.flyway.postgresql)
    // Boot 4: không có starter này thì Flyway không bao giờ chạy (chỉ nằm im trên classpath).
    implementation(libs.spring.boot.starter.flyway)
    // Pin CVE-2023-7272: `parsson` đến transitively qua jakarta.json và Boot BOM không quản lý
    // nó, nên đây là chỗ duy nhất ép được version.
    constraints {
        implementation(libs.parsson) {
            because("CVE-2023-7272: 1.0.0 (bản Boot kéo về) bị stack overflow khi parse JSON lồng sâu")
        }
    }
    implementation(libs.bucket4j.redis)
    runtimeOnly(libs.postgresql)
    // Ép parsson lên bản đã vá trên cả runtime classpath (constraint ở trên chỉ áp cho
    // dependency của chính module này, không đủ để ghi đè bản transitive của người khác).
    runtimeOnly(libs.parsson)

    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    implementation(libs.jsoup)
    implementation(libs.rome)
    // Boot 4 defaults to Jackson 3 for HTTP; blog/collector code still uses Jackson 2 APIs.
    implementation(libs.jackson.databind)
    implementation(libs.resilience4j.spring.boot4)
    implementation(libs.bucket4j.core)
    implementation(libs.caffeine)
    implementation(libs.aws.s3)
    implementation(libs.aws.auth)
    // ES search. Optional at runtime: search falls back to PostgreSQL when the
    // cluster is unreachable, so this dependency never becomes a hard startup
    // requirement — see app.search.elasticsearch.enabled.
    implementation(libs.spring.boot.starter.data.elasticsearch)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.elasticsearch)
    testImplementation(libs.testcontainers.junit.jupiter)
}
