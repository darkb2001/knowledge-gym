plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

dependencies {
    // Application layer: Spring annotations (compileOnly — domain không import, ArchUnit enforce)
    compileOnly("org.springframework:spring-context:6.1.6")
    compileOnly("org.springframework:spring-tx:6.1.6")

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.archunit.junit5)
}