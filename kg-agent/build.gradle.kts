plugins {
    `java-library`
}

dependencies {
    implementation(project(":kg-core"))
    api(project(":kg-infrastructure"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))
    implementation(libs.spring.kafka)
    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.slf4j:slf4j-api")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.spring.boot.starter.test)
}

// kg-agent = bounded module (ADR-001) cho collector/writer scheduling (m09+).
// Ở m1 là library module — Spring Boot main class thêm khi có CollectorApplication.
