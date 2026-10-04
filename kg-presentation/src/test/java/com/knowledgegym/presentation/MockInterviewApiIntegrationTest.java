package com.knowledgegym.presentation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.knowledgegym.content.application.ImportContentUseCase;
import com.knowledgegym.identity.domain.port.TokenService;
import com.knowledgegym.identity.domain.port.UserRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Resume (`GET /mock-interview/{id}`) + abandon (`POST /mock-interview/{id}/cancel`) trên
 * Postgres thật (Testcontainers) — những thứ fake unit test không chứng minh được:
 * <ul>
 *   <li>Phiên đọc lại từ DB bất kể status (ACTIVE/CANCELLED) qua đúng ownership clause.</li>
 *   <li>CHECK constraint `interview_sessions_status_check` chấp nhận 'CANCELLED' (migration V037).</li>
 *   <li>401 khi thiếu token (SecurityConfig `anyRequest().authenticated()`).</li>
 * </ul>
 * Dùng chung context/DB nên chạy theo {@code @Order}.
 */
@org.springframework.context.annotation.Import(TestEmailServiceConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MockInterviewApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("knowledgegym_interview")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.content.docs-path", MockInterviewApiIntegrationTest::fixtureDocsPath);
    }

    private static String fixtureDocsPath() {
        URL url = MockInterviewApiIntegrationTest.class.getResource("/fixtures/docs-mini");
        assertThat(url).as("fixture docs-mini phải có trong test resources").isNotNull();
        return Path.of(url.getPath()).toString();
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ImportContentUseCase importContentUseCase;
    @Autowired com.knowledgegym.content.domain.port.ModuleRepository moduleRepository;
    @Autowired UserRepository userRepository;
    @Autowired TokenService tokenService;

    private static String userToken, otherUserToken, topicId, sessionId;
    private static final List<String> startedQuestionIds = new ArrayList<>();

    private JsonNode response(MockHttpServletRequestBuilder request, int expected) throws Exception {
        return objectMapper.readTree(mockMvc.perform(request).andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(expected))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private MockHttpServletRequestBuilder postJson(String path, Object value, String token) throws Exception {
        return post(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(value));
    }

    @Test @Order(1) void setup() throws Exception {
        importContentUseCase.execute(fixtureDocsPath());
        UUID userId = register("interview-resume@example.com", "Resume Tester");
        userToken = tokenService.generateAccessToken(userId, "USER");
        otherUserToken = tokenService.generateAccessToken(register("interview-other@example.com", "Other Tester"), "USER");
        var module = moduleRepository.findBySlug("01-java-core").orElseThrow();
        topicId = module.getTopicId().toString();
    }

    @Test @Order(2) void startPhienDeResume() throws Exception {
        var started = response(postJson("/mock-interview/start",
                Map.of("topicId", topicId, "questionCount", 3, "mode", "TEXT"), userToken), 201);
        sessionId = started.get("session").get("id").asText();
        assertThat(started.get("session").get("status").asText()).isEqualTo("ACTIVE");
        assertThat(started.get("questions").size()).isEqualTo(3);
        for (JsonNode q : started.get("questions")) {
            startedQuestionIds.add(q.get("questionId").asText());
        }
    }

    @Test @Order(3) void resumeTraMetadataVaCauTheoThuTu() throws Exception {
        var resume = response(authed(get("/mock-interview/" + sessionId), userToken), 200);

        assertThat(resume.get("session").get("id").asText()).isEqualTo(sessionId);
        assertThat(resume.get("session").get("status").asText()).isEqualTo("ACTIVE");
        assertThat(resume.get("session").get("startedAt").isNull()).isFalse();
        assertThat(resume.get("totalQuestions").asInt()).isEqualTo(3);
        assertThat(resume.get("answeredCount").asInt()).isZero();
        assertThat(resume.get("questions").size()).isEqualTo(3);
        List<String> returned = new ArrayList<>();
        for (JsonNode q : resume.get("questions")) {
            assertThat(q.get("questionId").asText()).isNotBlank();
            assertThat(q.get("title").asText()).isNotBlank();
            assertThat(q.get("answered").asBoolean()).isFalse();
            returned.add(q.get("questionId").asText());
        }
        assertThat(returned).containsExactlyElementsOf(startedQuestionIds);
    }

    @Test @Order(4) void resumeKhongTokenTra401() throws Exception {
        response(get("/mock-interview/" + sessionId), 401);
    }

    @Test @Order(5) void resumePhienNguoiKhacTra404() throws Exception {
        response(authed(get("/mock-interview/" + sessionId), otherUserToken), 404);
    }

    @Test @Order(6) void cancelSetTrangThaiTerminal() throws Exception {
        var cancelled = response(authed(post("/mock-interview/" + sessionId + "/cancel"), userToken), 200);

        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("finishedAt").isNull()).isFalse();

        // Phiên đã đóng vẫn resume được (any status).
        var resume = response(authed(get("/mock-interview/" + sessionId), userToken), 200);
        assertThat(resume.get("session").get("status").asText()).isEqualTo("CANCELLED");
    }

    @Test @Order(7) void cancelLanHaiIdempotentVaSubmitSauCancelTra409() throws Exception {
        var again = response(authed(post("/mock-interview/" + sessionId + "/cancel"), userToken), 200);
        assertThat(again.get("status").asText()).isEqualTo("CANCELLED");

        // Phiên đã terminal: submit bị từ chối bằng 409 (ConflictException như code cũ).
        response(postJson("/mock-interview/" + sessionId + "/submit", Map.of("answers", List.of()), userToken), 409);
    }

    @Test @Order(8) void cancelPhienNguoiKhacTra404VaKhongTokenTra401() throws Exception {
        response(authed(post("/mock-interview/" + sessionId + "/cancel"), otherUserToken), 404);
        response(post("/mock-interview/" + sessionId + "/cancel"), 401);
    }

    private UUID register(String email, String displayName) throws Exception {
        String code = RegistrationTestSupport.code(mockMvc, email);
        response(postJson("/auth/register", Map.of("email", email, "password", "superSecret123",
                "confirmPassword", "superSecret123", "displayName", displayName, "verificationCode", code), "")
                .header("X-Forwarded-For", RegistrationTestSupport.nextIp()), 201);
        return userRepository.findByEmail(email).orElseThrow().getId();
    }
}
