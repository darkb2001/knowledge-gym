plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

// Boot 4.1.1 BOM pins Tomcat 11.0.24; Trivy CRITICAL CVE-2026-65182 needs ≥11.0.25.
// Spring Dependency Management reads this property before resolving the BOM.
extra["tomcat.version"] = "11.0.26"

dependencies {
    implementation(project(":kg-core"))
    implementation(project(":kg-infrastructure"))
    implementation(project(":kg-agent"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.springdoc.openapi)
    // DataIntegrityViolationException (register TOCTOU → 409)
    implementation("org.springframework:spring-tx")
    // AccessDeniedException + @PreAuthorize — kg-presentation dùng trực tiếp nên cần trên
    // compile classpath (runtime đã có sẵn qua kg-infrastructure, nhưng transitive deps
    // không expose ra compile classpath của module khác).
    implementation("org.springframework.security:spring-security-core")

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.platform.launcher)
    // Boot 4: MockMvc + TestRestTemplate live here, not in starter-test.
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.spring.boot.starter.data.redis) // M7 leaderboard cache integration assertions
    testImplementation("org.springframework:spring-jdbc") // integration assertions against persisted quiz rows
    testImplementation(libs.spring.security.test)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}
