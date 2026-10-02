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
    // kg-core dùng Logger ở application layer (search fallback phải log được khi degrade).
    // compileOnly vì runtime đã có qua spring-boot-starter-logging.
    compileOnly("org.slf4j:slf4j-api")

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation("org.mockito:mockito-core") // test doubles for ports in application use-case tests
    testImplementation(libs.archunit.junit5)
    testImplementation("org.assertj:assertj-core") // version manage bởi Spring Boot BOM
}
