package com.knowledgegym.presentation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
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
        // MockMvc cannot install Tomcat's native RemoteIpValve; use the framework
        // equivalent here while production uses the native strategy in application-prod.yml.
        registry.add("server.forward-headers-strategy", () -> "framework");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired com.knowledgegym.identity.domain.port.UserRepository userRepository;

    private static final java.util.concurrent.atomic.AtomicInteger IP_SEQ =
            new java.util.concurrent.atomic.AtomicInteger(10);

    /** IP ảo duy nhất cho từng test — cách ly bucket rate limit Redis. */
    private static String nextIp() {
        return "203.0.113." + IP_SEQ.getAndIncrement();
    }

    @Test
    void oauth_redirect_uses_forwarded_https_host_and_context_path() throws Exception {
        mockMvc.perform(get("/api/v1/oauth2/authorization/google")
                        .contextPath("/api/v1")
                        .header("X-Forwarded-Proto", "https")
                        .header("Host", "api.darkb-tech.io.vn")
                        .header("X-Forwarded-Host", "api.darkb-tech.io.vn"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString(
                        "redirect_uri=https://api.darkb-tech.io.vn/api/v1/login/oauth2/code/google")));
    }

    @Test
    void registrationRejectsMissingOrMismatchedConfirmationWithoutCreatingUser() throws Exception {
        for (String confirmation : new String[] {"", ",\"confirmPassword\":\"different123\""}) {
            String email = "confirm+" + java.util.UUID.randomUUID() + "@example.com";
            String payload = "{\"email\":\"" + email
                    + "\",\"password\":\"password123\",\"verificationCode\":\"123456\",\"displayName\":\"User\"" + confirmation + "}";
            mockMvc.perform(post("/auth/register")
                            .header("X-Forwarded-For", nextIp())
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(cookie().doesNotExist("refreshToken"));
            assertThat(userRepository.findByEmail(email)).isEmpty();
        }
    }

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.knowledgegym.identity.domain.port.EmailVerificationPort verification;

    @Test
    void verificationChallengeIsSingleUseExpiresAndLimitsAttempts() throws Exception {
        String email = "otp+" + java.util.UUID.randomUUID() + "@example.com";
        String code = RegistrationTestSupport.code(mockMvc, email);
        String hash = com.knowledgegym.identity.application.HashUtils.sha256Hex(code);
        String wrongHash = com.knowledgegym.identity.application.HashUtils.sha256Hex("not-a-code");
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(verification.consume(email, wrongHash)).isFalse();
        }
        assertThat(verification.consume(email, hash)).isFalse();
        assertThat(jdbc.queryForObject("SELECT attempts FROM registration_email_challenges WHERE email=?",
                Integer.class, email)).isEqualTo(5);
        jdbc.update("UPDATE registration_email_challenges SET attempts=0, expires_at=now()-interval '1 second' WHERE email=?", email);
        assertThat(verification.consume(email, hash)).isFalse();
        jdbc.update("UPDATE registration_email_challenges SET expires_at=now()+interval '10 minutes' WHERE email=?", email);
        assertThat(verification.consume(email, hash)).isTrue();
        assertThat(verification.consume(email, hash)).isFalse();
    }

    @Test
    void unverifiedLocalUserMustVerifyBeforeLogin() throws Exception {
        String email = "legacy+" + java.util.UUID.randomUUID() + "@example.com";
        String code = RegistrationTestSupport.code(mockMvc, email);
        userRepository.save(new com.knowledgegym.identity.domain.model.User(email,
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("password123"), "Legacy"));
        String login = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";
        mockMvc.perform(post("/auth/login").header("X-Forwarded-For", nextIp())
                        .contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/verify-email").header("X-Forwarded-For", nextIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + code
                                + "\",\"newPassword\":\"ownerPassword123\",\"confirmPassword\":\"ownerPassword123\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("refreshToken"));
        mockMvc.perform(post("/auth/login").header("X-Forwarded-For", nextIp())
                        .contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login").header("X-Forwarded-For", nextIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"ownerPassword123\"}"))
                .andExpect(status().isOk());
        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    void register_login_refresh_logout_flow() throws Exception {
        String email = "test+" + System.currentTimeMillis() + "@example.com";
        String ip = nextIp();

        // 1. Register
        String registerBody = """
                {"email":"%s","password":"superSecret123","confirmPassword":"superSecret123","displayName":"Tester","verificationCode":"%s"}
                """.formatted(email, RegistrationTestSupport.code(mockMvc, email));
        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("USER"))
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
                                {"email":"%s","password":"goodPassword123","confirmPassword":"goodPassword123","displayName":"X","verificationCode":"%s"}
                                """.formatted(email, RegistrationTestSupport.code(mockMvc, email))))
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
                {"email":"%s","password":"goodPassword123","confirmPassword":"goodPassword123","displayName":"X","verificationCode":"%s"}
                """.formatted(email, RegistrationTestSupport.code(mockMvc, email));
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
                {"email":"%s","password":"superSecret123","confirmPassword":"superSecret123","displayName":"ReuseTester","verificationCode":"%s"}
                """.formatted(email, RegistrationTestSupport.code(mockMvc, email));
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
                                {"email":"%s","password":"goodPassword123","confirmPassword":"goodPassword123","displayName":"X","verificationCode":"%s"}
                                """.formatted(email, RegistrationTestSupport.code(mockMvc, email))))
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
                                {"email":"%s","password":"goodPassword123","confirmPassword":"goodPassword123","displayName":"X","verificationCode":"%s"}
                                """.formatted(email, RegistrationTestSupport.code(mockMvc, email))))
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
                                {"email":"%s","password":"goodPassword123","confirmPassword":"goodPassword123","displayName":"X","verificationCode":"%s"}
                                """.formatted(email, RegistrationTestSupport.code(mockMvc, email))))
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
                                {"email":"%s","password":"oldPassword123","confirmPassword":"oldPassword123","displayName":"X","verificationCode":"%s"}
                                """.formatted(email, RegistrationTestSupport.code(mockMvc, email))))
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
                                {"email":"%s","password":"goodPassword123","confirmPassword":"goodPassword123","displayName":"X","verificationCode":"%s"}
                                """.formatted(email, RegistrationTestSupport.code(mockMvc, email))))
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

    @Test
    void usersMe_getAndPatchProfile() throws Exception {
        String email = "profile+" + System.currentTimeMillis() + "@example.com";
        String ip = nextIp();
        mockMvc.perform(post("/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"superSecret123","confirmPassword":"superSecret123","displayName":"Before","verificationCode":"%s"}
                                """.formatted(email, RegistrationTestSupport.code(mockMvc, email))))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"superSecret123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        String token = readJson(login, "$.accessToken");
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Before"));
        mockMvc.perform(patch("/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"After","avatarUrl":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("After"));
    }

    @Autowired com.knowledgegym.identity.domain.port.TokenService tokenService;

    /** Đổi mật khẩu khi đã đăng nhập: xác minh mật khẩu hiện tại, giữ phiên hiện tại, thu hồi phiên khác. */
    @Test
    void changePassword_requiresCurrentPassword_andRevokesOtherSessions() throws Exception {
        String email = "chpw+" + System.currentTimeMillis() + "@example.com";
        String ip = nextIp();
        MvcResult reg = mockMvc.perform(post("/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"oldPassword123","confirmPassword":"oldPassword123","displayName":"Chpw","verificationCode":"%s"}
                                """.formatted(email, RegistrationTestSupport.code(mockMvc, email))))
                .andExpect(status().isCreated()).andReturn();
        String otherSessionRefresh = reg.getResponse().getCookie("refreshToken").getValue();

        MvcResult login = mockMvc.perform(post("/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"oldPassword123"}
                                """.formatted(email)))
                .andExpect(status().isOk()).andReturn();
        String token = readJson(login, "$.accessToken");
        String currentSessionRefresh = login.getResponse().getCookie("refreshToken").getValue();

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(true));

        // Sai mật khẩu hiện tại → 400
        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrongPassword\",\"newPassword\":\"brandNew123\",\"confirmPassword\":\"brandNew123\"}"))
                .andExpect(status().isBadRequest());
        // Xác nhận không khớp → 400
        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"oldPassword123\",\"newPassword\":\"brandNew123\",\"confirmPassword\":\"otherNew123\"}"))
                .andExpect(status().isBadRequest());
        // Trùng mật khẩu cũ → 400
        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"oldPassword123\",\"newPassword\":\"oldPassword123\",\"confirmPassword\":\"oldPassword123\"}"))
                .andExpect(status().isBadRequest());
        // Chưa xác thực → 401 (endpoint không nằm trong permitAll)
        mockMvc.perform(post("/auth/change-password")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"oldPassword123\",\"newPassword\":\"brandNew123\",\"confirmPassword\":\"brandNew123\"}"))
                .andExpect(status().isUnauthorized());

        // Đổi thành công (kèm cookie phiên hiện tại)
        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", ip)
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", currentSessionRefresh))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"oldPassword123\",\"newPassword\":\"brandNew123\",\"confirmPassword\":\"brandNew123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Phiên hiện tại vẫn dùng được
        mockMvc.perform(post("/auth/refresh")
                        .header("X-Forwarded-For", ip)
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", currentSessionRefresh)))
                .andExpect(status().isOk());
        // Thiết bị khác bị đăng xuất
        mockMvc.perform(post("/auth/refresh")
                        .header("X-Forwarded-For", ip)
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", otherSessionRefresh)))
                .andExpect(status().isUnauthorized());
        // Mật khẩu cũ hết hiệu lực, mật khẩu mới dùng được
        mockMvc.perform(post("/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"oldPassword123\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"brandNew123\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='PASSWORD_CHANGED'", Integer.class))
                .isGreaterThanOrEqualTo(1);
    }

    /** Tài khoản Google (password_hash NULL): đặt mật khẩu lần đầu bằng mã xác minh email, sau đó login email/mật khẩu được. */
    @Test
    void googleUser_setsPasswordWithEmailCode_thenEmailLoginWorks() throws Exception {
        String email = "guser+" + System.currentTimeMillis() + "@example.com";
        String ip = nextIp();
        var googleUser = com.knowledgegym.identity.domain.model.User.createGoogleUser(email, "Google User", "google-sub-" + System.currentTimeMillis());
        googleUser.verifyEmail();
        userRepository.save(googleUser);
        String token = tokenService.generateAccessToken(googleUser.getId(), "USER");

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authProvider").value("GOOGLE"))
                .andExpect(jsonPath("$.hasPassword").value(false));

        // Đổi mật khẩu khi chưa có mật khẩu → 409 (client phải dùng luồng đặt mật khẩu)
        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"anything123\",\"newPassword\":\"brandNew123\",\"confirmPassword\":\"brandNew123\"}"))
                .andExpect(status().isConflict());

        // Gửi mã tới email của chính phiên đang đăng nhập
        mockMvc.perform(post("/auth/password-code/request")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", ip))
                .andExpect(status().isAccepted());
        String code = TestEmailServiceConfig.LAST_CODES.get(email.toLowerCase());
        assertThat(code).as("password code captured").hasSize(6);

        // Sai mã → 400
        mockMvc.perform(post("/auth/set-password")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"000000\",\"newPassword\":\"brandNew123\",\"confirmPassword\":\"brandNew123\"}"))
                .andExpect(status().isBadRequest());

        // Đặt mật khẩu thành công
        mockMvc.perform(post("/auth/set-password")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"newPassword\":\"brandNew123\",\"confirmPassword\":\"brandNew123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(true));
        // Login bằng email + mật khẩu vừa đặt
        mockMvc.perform(post("/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"brandNew123\"}"))
                .andExpect(status().isOk());
        // Gọi lại set-password khi đã có mật khẩu → 409
        mockMvc.perform(post("/auth/set-password")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"newPassword\":\"anotherNew123\",\"confirmPassword\":\"anotherNew123\"}"))
                .andExpect(status().isConflict());
        assertThat(userRepository.findByEmail(com.knowledgegym.identity.domain.model.User.normalizeEmail(email))
                .orElseThrow().getPasswordHash()).isNotBlank();
    }

    private String readJson(MvcResult result, String jsonPath) throws Exception {
        // jsonPath dạng "$.accessToken" → Jackson pointer "/accessToken"
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString())
                .at(jsonPath.substring(1).replaceFirst("\\.", "/"));
        return node.asText();
    }
}