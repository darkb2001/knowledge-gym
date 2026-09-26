plugins {
    java
}

dependencies {
    // Application layer dùng @Transactional/@Service annotation →
    // compileOnly Spring (domain code KHÔNG dùng trực tiếp, ArchUnit enforce).
    // Version lấy từ Spring Boot BOM — một phiên bản duy nhất theo libs.versions.toml.
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework:spring-tx")

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.archunit.junit5)
}