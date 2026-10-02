plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

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
    implementation(libs.bucket4j.redis)
    runtimeOnly(libs.postgresql)

    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    implementation(libs.jsoup)
    implementation(libs.rome)
    implementation(libs.resilience4j.spring.boot3)
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
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.elasticsearch)
    testImplementation(libs.testcontainers.junit.jupiter)
}
