plugins {
    `java-library`
}

dependencies {
    implementation(project(":kg-core"))
    api(project(":kg-infrastructure"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.spring.boot.starter.test)
}

// kg-agent = bounded module (ADR-001) cho collector/writer scheduling (m09+).
// Ở m1 là library module — Spring Boot main class thêm khi có CollectorApplication.