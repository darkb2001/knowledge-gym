package com.knowledgegym.presentation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.knowledgegym.content.application.ImportContentUseCase;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.TokenService;
import com.knowledgegym.identity.domain.port.UserRepository;
import com.knowledgegym.learning.domain.model.AttemptSource;
import com.knowledgegym.progress.application.RecordAttemptUseCase;
import com.knowledgegym.progress.domain.port.UserXpRepository;
import com.knowledgegym.progress.domain.port.StudyAttemptAnalytics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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

import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * m7 end-to-end trên Postgres + Redis thật (Testcontainers).
 *
 * <p>Assert những thứ unit test với fake **không** chứng minh được:
 * <ul>
 *   <li>Native upsert `ON CONFLICT` chạy được qua Hibernate — đây là chỗ mà cast `::` của PostgreSQL
 *       bị Hibernate parse thành tên parameter và làm **mọi** lần ghi attempt rollback.</li>
 *   <li>Advisory lock + `UPDATE users SET xp = xp + :delta` dưới 2 request song song cùng một câu.</li>
 *   <li>`AT TIME ZONE` bucket đúng ngày, heatmap đủ `days` phần tử kể cả count = 0.</li>
 *   <li>Leaderboard rebuild từ `users.xp` sau khi flush Redis.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestEmailServiceConfig.class)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DashboardApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("knowledgegym_dashboard")
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
        registry.add("app.content.docs-path", DashboardApiIntegrationTest::fixtureDocsPath);
    }

    private static String fixtureDocsPath() {
        URL url = DashboardApiIntegrationTest.class.getResource("/fixtures/docs-mini");
        assertThat(url).as("fixture docs-mini phải có trong test resources").isNotNull();
        return Path.of(url.getPath()).toString();
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ImportContentUseCase importContentUseCase;
    @Autowired UserRepository userRepository;
    @Autowired TokenService tokenService;
    @Autowired RecordAttemptUseCase recordAttemptUseCase;
    @Autowired UserXpRepository userXpRepository;
    @Autowired StudyAttemptAnalytics analytics;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;

    private static String userToken;
    private static UUID userId;
    private static String otherUserToken;
    private static UUID otherUserId;
    private static UUID moduleId;
    private static UUID otherModuleId;
    private static List<UUID> questionIds = new ArrayList<>();

    @BeforeAll
    static void resetStaticState() {
        userToken = null;
        userId = null;
        otherUserToken = null;
        otherUserId = null;
        moduleId = null;
        otherModuleId = null;
        questionIds = new ArrayList<>();
    }

    // ------------------------------------------------------------------ setup

    @Test
    @Order(1)
    void importFixturesAndCreateUsers() throws Exception {
        importContentUseCase.execute(fixtureDocsPath());

        userId = register("dash+" + System.currentTimeMillis() + "@example.com", "Dashboard Tester");
        userToken = tokenService.generateAccessToken(userId, "USER");
        otherUserId = register("dash-other+" + System.currentTimeMillis() + "@example.com", "Other Tester");
        otherUserToken = tokenService.generateAccessToken(otherUserId, "USER");

        List<UUID> modules = moduleIds();
        assertThat(modules).as("fixture có >= 2 module để test nhiều module").hasSizeGreaterThanOrEqualTo(2);
        moduleId = modules.get(0);
        otherModuleId = modules.get(1);
        questionIds = questionIdsOf(moduleId);
        assertThat(questionIds).as("fixture cần đủ câu cho regression tests M7").hasSizeGreaterThanOrEqualTo(6);
    }

    // ------------------------------------------------------------------ write path (P0 regression)

    /**
     * Regression bắt lỗi native upsert: `:correctDelta::numeric` làm Hibernate ném
     * "No argument for named parameter" ⇒ **mọi** lần ghi attempt đều rollback. Test này fail nếu
     * ai quay lại dùng cast `::` của PostgreSQL trong named query.
     */
    @Test
    @Order(2)
    void writingAnAttemptMaterializesProgressInTheDatabase() throws Exception {
        mockMvc.perform(post("/srs/enroll")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"moduleId":"%s"}
                                """.formatted(moduleId)))
                .andExpect(status().isCreated());

        String cardId = anyDueCardId(userToken, moduleId);
        mockMvc.perform(post("/srs/review/{cardId}", cardId)
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quality":2,"timeMs":4200}
                                """))
                .andExpect(status().isOk());

        assertThat(progressRow(userId, moduleId))
                .as("native upsert phải chạy được: 1 attempt, 1 correct, mastery 100.00")
                .isEqualTo("1|1|100.00");
        assertThat(xpInDb(userId))
                .as("lần đầu của câu → xpFor(FLASHCARD, true) = 10")
                .isEqualTo(10);
    }

    /** Mastery phải tính từ tổng luỹ kế: 2 đúng/3 làn ⇒ 66.67, không phải tỉ lệ của lần ghi thứ hai. */
    @Test
    @Order(3)
    void masteryUsesCumulativeTotalsAcrossWrites() throws Exception {
        assertThat(progressRow(userId, moduleId)).isEqualTo("1|1|100.00");

        UUID q2 = questionIds.get(1);
        UUID q3 = questionIds.get(2);
        recordAttemptUseCase.execute(userId, List.of(
                attempt(q2, true), attempt(q3, false)), Instant.now());

        assertThat(progressRow(userId, moduleId))
                .as("(1+1)/(1+2) = 66.67 — nếu tính từ delta thì lần 2 ra 50.00")
                .isEqualTo("3|2|66.67");
    }

    @Test
    @Order(4)
    void quizWithManyAnswersUpsertsOneRowPerModule() throws Exception {
        long before = progressRowCount(userId, moduleId);
        UUID q4 = questionIds.get(3);
        UUID q5 = questionIds.get(4);

        recordAttemptUseCase.execute(userId, List.of(
                attempt(q4, true), attempt(q5, true)), Instant.now());

        assertThat(progressRowCount(userId, moduleId))
                .as("1 module ⇒ vẫn 1 row, không phải 1 row/câu")
                .isEqualTo(before);
        assertThat(progressRow(userId, moduleId)).isEqualTo("5|4|80.00");
    }

    // ------------------------------------------------------------------ rollback (cùng tx)

    /**
     * Cùng tx là lý do duy nhất để ghi XP/progress ở đây: nếu insert attempt nổ thì `user_progress`
     * **không** được có row mới và `users.xp` **không** đổi.
     */
    @Test
    @Order(5)
    void rollbackLeavesProgressAndXpUntouched() throws Exception {
        int xpBefore = xpInDb(userId);
        String progressBefore = progressRow(userId, moduleId);
        long rowsBefore = progressRowCount(otherUserId, otherModuleId);

        blockAttemptInserts();
        try {
        assertThatThrownBy(() -> recordAttemptUseCase.execute(userId,
                    List.of(attempt(questionIds.get(5), true)), Instant.now()))
                    .hasStackTraceContaining("study_attempts bị chặn");
        } finally {
            unblockAttemptInserts();
        }

        assertThat(xpInDb(userId)).as("xp không được đổi khi tx rollback").isEqualTo(xpBefore);
        assertThat(progressRow(userId, moduleId)).isEqualTo(progressBefore);
        assertThat(progressRowCount(otherUserId, otherModuleId))
                .as("rollback không được tạo row cho module khác")
                .isEqualTo(rowsBefore);
    }

    // ------------------------------------------------------------------ P5: XP double-count

    /**
     * Finding P5: `SELECT` rồi `INSERT` là check-then-act. Hai tx đồng thời cùng một câu sẽ cùng
     * thấy "chưa từng attempt" ⇒ cùng cộng XP nếu không có advisory lock. Test assert cả hai đường
     * (cột materialize vs recompute) khớp nhau.
     */
    @Test
    @Order(6)
    void concurrentAttemptsOnTheSameQuestionAwardXpOnlyOnce() throws Exception {
        UUID shared = questionIds.get(5); // insert test rollback trước đó đã rollback: đây vẫn là câu mới
        int xpBefore = xpInDb(userId);
        String progressBefore = progressRow(userId, moduleId);

        var barrier = new CyclicBarrier(2);
        Callable<Void> flashcard = () -> {
            barrier.await(30, TimeUnit.SECONDS);
            recordAttemptUseCase.execute(userId, List.of(
                    new RecordAttemptUseCase.AttemptFact(shared, AttemptSource.FLASHCARD, true)), Instant.now());
            return null;
        };
        Callable<Void> practice = () -> {
            barrier.await(30, TimeUnit.SECONDS);
            recordAttemptUseCase.execute(userId, List.of(
                    new RecordAttemptUseCase.AttemptFact(shared, AttemptSource.PRACTICE, true)), Instant.now());
            return null;
        };

        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(flashcard);
            var two = executor.submit(practice);
            one.get(60, TimeUnit.SECONDS);
            two.get(60, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(xpInDb(userId) - xpBefore)
                .as("hai tx tranh cùng câu mới → đúng một tx cộng XP theo source thắng lock")
                .isIn(5, 10);
        assertThat(progressRow(userId, moduleId))
                .as("nhưng attempt vẫn được ghi: +2 total, +2 correct")
                .isEqualTo(incrementAttempts(progressBefore, 2, 2));
        assertThat(xpOf(userId))
                .as("users.xp phải khớp recompute từ study_attempts (2 đường không lệch)")
                .isEqualTo(xpInDb(userId));
    }

    // ------------------------------------------------------------------ read endpoints

    @Test
    @Order(7)
    void radarReturnsOneEntryPerModuleWithName() throws Exception {
        mockMvc.perform(get("/dashboard/radar").header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules.length()").value(1))
                .andExpect(jsonPath("$.modules[0].moduleId").value(moduleId.toString()))
                .andExpect(jsonPath("$.modules[0].name").isNotEmpty())
                .andExpect(jsonPath("$.modules[0].masteryPct").isNumber());
    }

    @Test
    @Order(8)
    void progressEndpointMatchesUserProgressTable() throws Exception {
        MvcResult result = mockMvc.perform(get("/users/me/progress")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].moduleId").value(moduleId.toString()))
                .andReturn();

        String[] db = progressRow(userId, moduleId).split("\\|");
        assertThat(json(result, "$[0].totalAttempts")).isEqualTo(db[0]);
        assertThat(new BigDecimal(json(result, "$[0].masteryPct")))
                .isEqualByComparingTo(new BigDecimal(db[2]));
    }

    /**
     * Streak tính on-read, không đọc `user_progress.streak_days` — học nhiều lần trong **cùng một
     * ngày** chỉ là 1 ngày hoạt động.
     */
    @Test
    @Order(9)
    void streakCountsDistinctDaysNotAttempts() throws Exception {
        mockMvc.perform(get("/users/me/stats").header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(1))
                .andExpect(jsonPath("$.xp").value(xpInDb(userId)));

        assertThat(streakDaysColumn(userId, moduleId))
                .as("m7 không ghi streak_days — cột phải vẫn là 0 để không có 2 nguồn sự thật")
                .isZero();
    }

    /** Streak là "học hôm qua nhưng chưa học hôm nay" vẫn còn — biên ngày chưa kết thúc. */
    @Test
    @Order(10)
    void streakSurvivesWhileTheCurrentDayIsStillOpen() throws Exception {
        UUID yesterday = UUID.randomUUID();
        seedAttemptAt(userId, questionIds.get(5), LocalDate.now().minusDays(1)
                .atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant());

        assertThat(streakFromStats(userToken))
                .as("hôm nay + hôm qua = 2 ngày liên tiếp")
                .isEqualTo(2);
    }

    /**
     * Biên múi giờ: `app.progress.timezone` quyết định ngày, không phải session TZ của Postgres.
     * 16:59 UTC = 23:59 giờ VN (cùng ngày), 17:01 UTC = 00:01 giờ VN (ngày kế tiếp).
     */
    @Test
    @Order(11)
    void heatmapBucketUsesTheConfiguredTimeZone() throws Exception {
        UUID tzUser = register("dash-tz+" + System.currentTimeMillis() + "@example.com", "TZ Tester");
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        seedAttemptAt(tzUser, questionIds.get(0),
                today.minusDays(1).atTime(16, 59).toInstant(java.time.ZoneOffset.UTC));
        seedAttemptAt(tzUser, questionIds.get(0),
                today.minusDays(1).atTime(17, 1).toInstant(java.time.ZoneOffset.UTC));
        // Invoke the adapter directly as well as through HTTP so SQL translation exposes DB failures.
        assertThat(analytics.heatmap(tzUser, today.minusDays(6), today,
                java.time.ZoneId.of("Asia/Ho_Chi_Minh"))).hasSize(7);

        String token = tokenService.generateAccessToken(tzUser, "USER");
        MvcResult result = mockMvc.perform(get("/dashboard/heatmap").param("days", "7")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andReturn();

        JsonNode days = objectMapper.readTree(body(result));
        assertThat(days.get(5).get("date").asText())
                .as("23:59 giờ VN rơi vào hôm qua")
                .isEqualTo(today.minusDays(1).toString());
        assertThat(days.get(5).get("count").asLong()).isEqualTo(1);
        assertThat(days.get(6).get("date").asText())
                .as("00:01 giờ VN rơi vào hôm nay")
                .isEqualTo(today.toString());
        assertThat(days.get(6).get("count").asLong()).isEqualTo(1);
        assertThat(days.get(3).get("count").asLong()).as("ngày rỗng vẫn được trả").isZero();
    }

    @Test
    @Order(12)
    void heatmapReturnsExactlyTheRequestedNumberOfDaysIncludingEmptyOnes() throws Exception {
        MvcResult result = mockMvc.perform(get("/dashboard/heatmap").param("days", "30")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(30))
                .andReturn();

        JsonNode days = objectMapper.readTree(body(result));
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(days.get(0).get("date").asText()).isEqualTo(today.minusDays(29).toString());
        assertThat(days.get(29).get("date").asText()).isEqualTo(today.toString());
        assertThat(days.get(0).get("count").asLong()).isZero();
    }

    @Test
    @Order(13)
    void heatmapRejectsOutOfRangeDays() throws Exception {
        mockMvc.perform(get("/dashboard/heatmap").param("days", "0")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/dashboard/heatmap").param("days", "366")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ m7b: leaderboard

    @Test
    @Order(14)
    void leaderboardRebuildsFromPostgresAfterFlushAndIsDeterministic() throws Exception {
        redis.delete("lb:global");
        redis.delete("lb:global:empty");

        MvcResult first = mockMvc.perform(get("/dashboard/leaderboard")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode firstUsers = objectMapper.readTree(body(first));
        assertThat(firstUsers.size()).isPositive();
        assertThat(userIdsOf(firstUsers)).contains(userId.toString());

        assertThat(redis.getExpire("lb:global"))
                .as("TTL bắt buộc để key không leak")
                .isGreaterThan(0L);

        // Flush giữa 2 lần gọi ⇒ cùng kết quả, cùng thứ tự (success criterion).
        String tokenAtSecondCall = tokenService.generateAccessToken(userId, "USER");
        assertThat(tokenAtSecondCall).isNotNull();
        redis.delete("lb:global");
        MvcResult second = mockMvc.perform(get("/dashboard/leaderboard")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(userIdsOf(objectMapper.readTree(body(second))))
                .as("flush Redis rồi rebuild từ PG phải ra đúng thứ tự cũ")
                .isEqualTo(userIdsOf(firstUsers));
    }

    @Test
    @Order(15)
    void userWithoutXpIsAbsentFromTheLeaderboard() throws Exception {
        UUID lurker = register("dash-lurker+" + System.currentTimeMillis() + "@example.com", "Lurker");
        assertThat(xpInDb(lurker)).isZero();

        redis.delete("lb:global");
        redis.delete("lb:global:empty");

        MvcResult result = mockMvc.perform(get("/dashboard/leaderboard")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(userIdsOf(objectMapper.readTree(body(result))))
                .as("policy: xp = 0 không lên BXH")
                .doesNotContain(lurker.toString());
    }

    /** Cache rỗng phải được phân biệt với cache miss, nếu không mỗi request lại quét `users`. */
    @Test
    @Order(16)
    void emptyLeaderboardIsCachedInsteadOfRebuiltEveryRequest() throws Exception {
        redis.delete("lb:global");
        redis.delete("lb:global:empty");
        execute("UPDATE users SET xp = 0");

        try {
            mockMvc.perform(get("/dashboard/leaderboard").header("Authorization", bearer(userToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));

            assertThat(redis.hasKey("lb:global:empty"))
                    .as("BXH rỗng ⇒ marker TTL được đặt để request sau không quét DB lại")
                    .isTrue();
            assertThat(redis.getExpire("lb:global:empty")).isGreaterThan(0L);

            mockMvc.perform(get("/dashboard/leaderboard").header("Authorization", bearer(userToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
            assertThat(redis.hasKey("lb:global")).as("không có set rỗng nào được ghi").isFalse();
        } finally {
            execute("UPDATE users SET xp = 0");
        }
    }

    // ------------------------------------------------------------------ auth

    @Test
    @Order(17)
    void everyDashboardEndpointRequiresAToken() throws Exception {
        for (String path : List.of("/dashboard/radar", "/dashboard/heatmap",
                "/dashboard/leaderboard", "/users/me/progress", "/users/me/stats")) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    /** `userId` lấy từ JWT, không từ query — user khác không thấy tiến độ của nhau. */
    @Test
    @Order(18)
    void progressIsScopedToThePrincipal() throws Exception {
        mockMvc.perform(get("/users/me/progress").header("Authorization", bearer(otherUserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/users/me/stats").header("Authorization", bearer(otherUserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xp").value(0))
                .andExpect(jsonPath("$.currentStreak").value(0));
    }

    // ------------------------------------------------------------------ helpers

    private static RecordAttemptUseCase.AttemptFact attempt(UUID questionId, boolean correct) {
        return new RecordAttemptUseCase.AttemptFact(questionId, AttemptSource.PRACTICE, correct,
                1200, null, BigDecimal.valueOf(correct ? 100 : 0));
    }

    private UUID register(String email, String displayName) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .header("X-Forwarded-For", RegistrationTestSupport.nextIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"superSecret123","confirmPassword":"superSecret123","displayName":"%s","verificationCode":"%s"}
                                """.formatted(email, displayName, RegistrationTestSupport.code(mockMvc, email))))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmail(email).orElseThrow();
        return user.getId();
    }

    private List<UUID> moduleIds() throws Exception {
        MvcResult result = mockMvc.perform(get("/modules").header("Authorization", bearer(userToken)))
                .andExpect(status().isOk()).andReturn();
        List<UUID> ids = new ArrayList<>();
        objectMapper.readTree(body(result)).forEach(module -> ids.add(UUID.fromString(module.get("id").asText())));
        return ids;
    }

    private List<UUID> questionIdsOf(UUID targetModule) throws Exception {
        MvcResult result = mockMvc.perform(get("/questions").param("moduleId", targetModule.toString())
                        .param("size", "50").header("Authorization", bearer(userToken)))
                .andExpect(status().isOk()).andReturn();
        JsonNode content = objectMapper.readTree(body(result)).get("items");
        assertThat(content).as("endpoint phải trả PageResponse có `items`").isNotNull();
        List<UUID> ids = new ArrayList<>();
        content.forEach(question -> ids.add(UUID.fromString(question.get("id").asText())));
        return ids;
    }

    private String anyDueCardId(String token, UUID targetModule) throws Exception {
        MvcResult result = mockMvc.perform(get("/srs/due").param("moduleId", targetModule.toString())
                        .param("limit", "1").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(body(result)).get(0).get("cardId").asText();
    }

    private static List<String> userIdsOf(JsonNode rows) {
        List<String> ids = new ArrayList<>();
        rows.forEach(row -> ids.add(row.get("userId").asText()));
        return ids;
    }

    private int streakFromStats(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/users/me/stats").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(body(result)).get("currentStreak").asInt();
    }

    /** Trả `total|correct|mastery` — so sánh nguyên chuỗi để bắt mọi lệch nhỏ. */
    private String progressRow(UUID targetUser, UUID targetModule) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT total_attempts, correct_count, mastery_pct "
                     + "FROM user_progress WHERE user_id = ? AND module_id = ?")) {
            statement.setObject(1, targetUser);
            statement.setObject(2, targetModule);
            try (var rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getInt(1) + "|" + rs.getInt(2) + "|" + rs.getBigDecimal(3).toPlainString();
            }
        }
    }

    private long progressRowCount(UUID targetUser, UUID targetModule) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT count(*) FROM user_progress WHERE user_id = ? AND module_id = ?")) {
            statement.setObject(1, targetUser);
            statement.setObject(2, targetModule);
            try (var rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private int streakDaysColumn(UUID targetUser, UUID targetModule) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT streak_days FROM user_progress WHERE user_id = ? AND module_id = ?")) {
            statement.setObject(1, targetUser);
            statement.setObject(2, targetModule);
            try (var rs = statement.executeQuery()) {
                assertThat(rs.next()).as("row user_progress phải tồn tại").isTrue();
                return rs.getInt(1);
            }
        }
    }

    private int xpInDb(UUID targetUser) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT xp FROM users WHERE id = ?")) {
            statement.setObject(1, targetUser);
            try (var rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int xpOf(UUID targetUser) {
        return userXpRepository.xpOf(targetUser);
    }

    /** Cộng dồn một hàng `total|correct|mastery` — dùng để so sánh sau khi thêm attempt. */
    private static String incrementAttempts(String row, int totalDelta, int correctDelta) {
        String[] parts = row.split("\\|");
        int total = Integer.parseInt(parts[0]) + totalDelta;
        int correct = Integer.parseInt(parts[1]) + correctDelta;
        BigDecimal mastery = BigDecimal.valueOf(correct).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, java.math.RoundingMode.HALF_UP);
        return total + "|" + correct + "|" + mastery.toPlainString();
    }

    /** Ghi attempt với `attempted_at` chỉ định — không có đường public nào cho phép backdate. */
    private void seedAttemptAt(UUID targetUser, UUID questionId, Instant at) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("INSERT INTO study_attempts "
                     + "(id, user_id, question_id, source, is_correct, attempted_at) "
                     + "VALUES (gen_random_uuid(), ?, ?, 'PRACTICE', true, ?)")) {
            statement.setObject(1, targetUser);
            statement.setObject(2, questionId);
            statement.setObject(3, at.atOffset(java.time.ZoneOffset.UTC));
            statement.executeUpdate();
        }
    }

    private void execute(String sql) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void blockAttemptInserts() throws Exception {
        execute("""
                CREATE FUNCTION kg_test_block_attempt_dash() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'study_attempts bị chặn để test rollback';
                END;
                $$ LANGUAGE plpgsql;
                """);
        execute("CREATE TRIGGER kg_test_block_attempt_dash BEFORE INSERT ON study_attempts "
                + "FOR EACH ROW EXECUTE FUNCTION kg_test_block_attempt_dash()");
    }

    private void unblockAttemptInserts() throws Exception {
        execute("DROP TRIGGER IF EXISTS kg_test_block_attempt_dash ON study_attempts");
        execute("DROP FUNCTION IF EXISTS kg_test_block_attempt_dash()");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /** `getContentAsString()` không tham số dùng ISO-8859-1 → tiếng Việt thành mojibake. */
    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String json(MvcResult result, String path) throws Exception {
        String pointer = path.startsWith("$") ? path.substring(1) : path;
        pointer = pointer.replaceAll("\\[(\\d+)\\]", "/$1").replace('.', '/');
        JsonNode node = new ObjectMapper().readTree(body(result)).at(pointer);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }
}
