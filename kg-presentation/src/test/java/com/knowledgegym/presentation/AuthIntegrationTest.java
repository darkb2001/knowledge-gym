package com.knowledgegym.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end auth flow: register → login → refresh → logout.
 * Testcontainers Postgres + Redis. Security filters ON (full chain).
 *
 * Mỗi test dùng X-Forwarded-For IP riêng — bucket rate limit (Redis) chung
 * giữa các test trong cùng class nên phải cách ly để không rơi vào 429 chéo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@org.springframework.context.annotation.Import(TestEmailServiceConfig.class)
class AuthIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("knowledgegym_auth")
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
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final java.util.concurrent.atomic.AtomicInteger IP_SEQ =
            new java.util.concurrent.atomic.AtomicInteger(10);

    /** IP ảo duy nhất cho từng test — cách ly bucket rate limit Redis. */
    private static String nextIp() {
        return "203.0.113." + IP_SEQ.getAndIncrement();
    }

    @Test
    void register_login_refresh_logout_flow() throws Exception {
        String email = "test+" + System.currentTimeMillis() + "@example.com";
        String ip = nextIp();

        // 1. Register
        String registerBody = """
                {"email":"%s","password":"superSecret123","displayName":"Tester"}
                """.formatted(email);
        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(cookie().exists("refreshToken"))
                .andReturn();

        String accessToken = readJson(registerResult, "$.accessToken");
        String refreshCookie = registerResult.getResponse().getCookie("refreshToken").getValue();

        // 2. Login (with same credentials)
        String loginBody = """
                {"email":"%s","password":"superSecret123"}
                """.formatted(email);
        mockMvc.perform(post("/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(cookie().exists("refreshToken"));

        // 3. Refresh (use cookie)
        mockMvc.perform(post("/auth/refresh")
                        .header("X-Forwarded-For", ip)
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(cookie().exists("refreshToken"));

        // 4. Logout
        mockMvc.perform(post("/auth/logout")
                        .header("X-Forwarded-For", ip)
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshCookie)))
                .andExpect(status().isNoContent());

        // 5. Reuse refresh token after logout → 401 (reuse detection / family revoked)
        mockMvc.perform(post("/auth/refresh")
                        .header("X-Forwarded-For", ip)
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshCookie)))
                .andExpect(status().isUnauthorized());

        assertThat(accessToken).isNotBlank();
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        String email = "wrong+" + System.currentTimeMillis() + "@example.com";
        String ip = nextIp();
        // Register first
        mockMvc.perform(post("/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"goodPassword123","displayName":"X"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        // Wrong password
        mockMvc.perform(post("/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrongPassword"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_duplicateEmail_returns400() throws Exception {
        String email = "dup+" + System.currentTimeMillis() + "@example.com";
        String ip = nextIp();
        String body = """
                {"email":"%s","password":"goodPassword123","displayName":"X"}
                """.formatted(email);
        mockMvc.perform(post("/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void refresh_reuseAfterRotation_revokesFamily() throws Exception {
        // Đăng ký user
        String email = "reuse+" + System.currentTimeMillis() + "@example.com";
        String ip = nextIp();
        String registerBody = """
                {"email":"%s","password":"superSecret123","displayName":"ReuseTester"}
                """.formatted(email);
        MvcResult reg = mockMvc.perform(post("/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isCreated()).andReturn();
        String originalRefresh = reg.getResponse().getCookie("refreshToken").getValue();

        // Lần 1: refresh thành công (rotation tạo token mới, old vào blacklist)
        mockMvc.perform(post("/auth/refresh")
                        .header("X-Forwarded-For", ip)
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", originalRefresh)))
                .andExpect(status().isOk());

        // Lần 2: dùng lại token cũ → phải 401 (family revoke)
        mockMvc.perform(post("/auth/refresh")
                        .header("X-Forwarded-For", ip)
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", originalRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rateLimit_login_429After5Attempts() throws Exception {
        String email = "rate+" + System.currentTimeMillis() + "@example.com";
        String ip = nextIp();
        mockMvc.perform(post("/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"goodPassword123","displayName":"X"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        // 5 lần login sai liên tiếp
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","password":"wrongPassword"}
                                    """.formatted(email)));
        }
        // Lần thứ 6 → 429
        mockMvc.perform(post("/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrongPassword"}
                                """.formatted(email)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void forgotPassword_returnsGeneric200_andDoesNotEnumerate() throws Exception {
        String ip = nextIp();
        // Email không tồn tại — vẫn trả 200 (chống enumeration)
        mockMvc.perform(post("/auth/forgot-password")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nonexistent@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Email tồn tại — vẫn 200 generic
        String email = "forgot+" + System.currentTimeMillis() + "@example.com";
        mockMvc.perform(post("/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"goodPassword123","displayName":"X"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/auth/forgot-password")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void refreshCookie_isHttpOnly_andSecureOffInTestProfile() throws Exception {
        String email = "cookie+" + System.currentTimeMillis() + "@example.com";
        MvcResult reg = mockMvc.perform(post("/auth/register")
                        .header("X-Forwarded-For", nextIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"goodPassword123","displayName":"X"}
                                """.formatted(email)))
                .andExpect(status().isCreated()).andReturn();
        var cookie = reg.getResponse().getCookie("refreshToken");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).as("refresh cookie must be httpOnly").isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
    }

    @Test
    void forgotPassword_fullFlow_resetsPassword_andRevokesRefreshTokens() throws Exception {
        String email = "flow+" + System.currentTimeMillis() + "@example.com";
        String ip = nextIp();
        // Register + giữ cookie để kiểm tra revoke sau reset
        MvcResult reg = mockMvc.perform(post("/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"oldPassword123","displayName":"X"}
                                """.formatted(email)))
                .andExpect(status().isCreated()).andReturn();
        String oldRefresh = reg.getResponse().getCookie("refreshToken").getValue();

        // Forgot password → gửi code (bắt vào TestEmailServiceConfig)
        mockMvc.perform(post("/auth/forgot-password")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        String code = TestEmailServiceConfig.LAST_CODES.get(email.toLowerCase());
        assertThat(code).as("reset code captured by test email service").hasSize(6);

        // Reset password bằng code đúng
        mockMvc.perform(post("/auth/reset-password")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s","newPassword":"brandNewPass456"}
                                """.formatted(email, code)))
                .andExpect(status().isOk());

        // Login mật khẩu cũ → 401; mật khẩu mới → 200
        mockMvc.perform(post("/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"oldPassword123"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"brandNewPass456"}
                                """.formatted(email)))
                .andExpect(status().isOk());

        // Refresh token cũ đã bị revoke family → 401
        mockMvc.perform(post("/auth/refresh")
                        .header("X-Forwarded-For", ip)
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", oldRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPassword_wrongCode5Times_thenCorrectCode_rejected() throws Exception {
        String email = "attempts+" + System.currentTimeMillis() + "@example.com";
        String ip = nextIp();
        mockMvc.perform(post("/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"goodPassword123","displayName":"X"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/forgot-password")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        String code = TestEmailServiceConfig.LAST_CODES.get(email.toLowerCase());
        assertThat(code).hasSize(6);

        // 5 lần sai → code invalidate (mỗi lần sai là 401 hoặc 400)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/reset-password")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","code":"000000","newPassword":"newPassword123"}
                                    """.formatted(email)))
                    .andExpect(status().is4xxClientError());
        }
        // Dùng code ĐÚNG sau khi đã sai 5 lần → vẫn bị từ chối (code đã invalidate)
        mockMvc.perform(post("/auth/reset-password")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s","newPassword":"newPassword123"}
                                """.formatted(email, code)))
                .andExpect(status().is4xxClientError());
    }

    private String readJson(MvcResult result, String jsonPath) throws Exception {
        // jsonPath dạng "$.accessToken" → Jackson pointer "/accessToken"
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString())
                .at(jsonPath.substring(1).replaceFirst("\\.", "/"));
        return node.asText();
    }
}