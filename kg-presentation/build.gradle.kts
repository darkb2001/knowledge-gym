import java.security.MessageDigest

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
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.springdoc.openapi)
    implementation(libs.jackson.databind)
    implementation("ch.qos.logback:logback-classic")
    implementation("io.opentelemetry:opentelemetry-api")
    testImplementation("io.opentelemetry:opentelemetry-sdk")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.kafka:spring-kafka")
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
    testImplementation(libs.spring.boot.starter.security) // isolated admin HTTP security tests
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}

// Real Kafka propagation with the SAME checksum-pinned agent as the image.
// Deliberately separate from normal tests: caller supplies the verified agent.
tasks.test { exclude("**/KafkaAgentPropagationTest.class") }
tasks.register<Test>("telemetryAgentTest") {
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("**/KafkaAgentPropagationTest.class")
    maxHeapSize = "512m"
    environment("OTEL_SERVICE_NAME", "knowledge-gym-canary")
    environment("OTEL_TRACES_EXPORTER", "none")
    environment("OTEL_METRICS_EXPORTER", "none")
    environment("OTEL_LOGS_EXPORTER", "none")
    environment("OTEL_PROPAGATORS", "tracecontext")
    environment("OTEL_TRACES_SAMPLER", "always_on")
    environment("OTEL_JAVAAGENT_LOGGING", "none")
    environment("OTEL_SPAN_EVENT_COUNT_LIMIT", "0")
    environment("OTEL_SPAN_LINK_COUNT_LIMIT", "0")
    doFirst {
        val agent = file(providers.gradleProperty("otelAgentPath").get())
        val hash =
            MessageDigest
                .getInstance("SHA-256")
                .digest(agent.readBytes())
                .joinToString("") { "%02x".format(it) }
        require(hash == "f787eb6c7f3d18e69a431e108a15278d25ee37f83d68b678f621e063f3988f82") {
            "Agent checksum mismatch; use the documented v2.32.0 artifact"
        }
        jvmArgs("-javaagent:" + agent.absolutePath)
    }
}
