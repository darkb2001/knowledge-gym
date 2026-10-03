package com.knowledgegym.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke: Spring context boot + Flyway migrate + ddl-auto=validate + /actuator/health.
 * m3: Redis thật (Testcontainers) — refresh-token cache + rate limiter cần kết nối.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Testcontainers
class ApplicationSmokeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("knowledgegym_smoke")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoadsWithFlywayAndValidate() {
        // Context startup = Flyway migrate + Hibernate ddl-auto=validate against 32 entities.
    }

    @Test
    void actuatorHealthReturns200() throws Exception {
        var result = mockMvc.perform(get("/actuator/health")).andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                .as("health body: %s", result.getResponse().getContentAsString())
                .isEqualTo(200);
    }

    @Test
    void actuatorPrometheusReturnsTextMetrics() throws Exception {
        var result = mockMvc.perform(get("/actuator/prometheus")).andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                .as("prometheus body: %s", result.getResponse().getContentAsString())
                .isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentType())
                .contains("text/plain");
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                .contains("jvm_memory_used_bytes");
    }
}