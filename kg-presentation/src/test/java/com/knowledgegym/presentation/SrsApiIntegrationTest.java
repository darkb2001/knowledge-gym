package com.knowledgegym.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegym.content.application.ImportContentUseCase;
import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.QuestionQuery;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.TokenService;
import com.knowledgegym.identity.domain.port.UserRepository;
import jakarta.servlet.ServletException;
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

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * m5 end-to-end trên Postgres thật (Testcontainers): enroll → due → review.
 *
 * <p>Assert những thứ unit test với fake **không** chứng minh được:
 * <ul>
 *   <li>`ON CONFLICT (user_id, question_id) DO NOTHING` + `RETURNING id` của native query.</li>
 *   <li>Row `study_attempts` thực sự được ghi (đọc lại từ DB, không qua port ghi).</li>
 *   <li>Cách ly giữa user: `userId` lấy từ JWT, không từ body.</li>
 * </ul>
 *
 * <p>Dùng chung context/DB nên chạy theo `@Order`: import fixture trước, các test sau dùng dữ liệu.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SrsApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("knowledgegym_srs")
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
        registry.add("app.content.docs-path", SrsApiIntegrationTest::fixtureDocsPath);
    }

    private static String fixtureDocsPath() {
        URL url = SrsApiIntegrationTest.class.getResource("/fixtures/docs-mini");
        assertThat(url).as("fixture docs-mini phải có trong test resources").isNotNull();
        return Path.of(url.getPath()).toString();
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ImportContentUseCase importContentUseCase;
    @Autowired QuestionRepository questionRepository;
    @Autowired UserRepository userRepository;
    @Autowired TokenService tokenService;
    @Autowired javax.sql.DataSource dataSource;
    // Port kg-core (không phải adapter) + PlatformTransactionManager của Spring: đều không thuộc
    // package bị `PresentationLayerArchTest` cấm (infrastructure.persistence / JPA).
    @Autowired com.knowledgegym.learning.domain.port.SRSCardRepository srsCardRepository;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    private static String userToken;
    private static UUID userId;
    private static String otherUserToken;
    private static UUID otherUserId;
    private static String moduleId;
    private static String moduleSlug;

    @BeforeAll
    static void resetStaticState() {
        userToken = null;
        userId = null;
        otherUserToken = null;
        otherUserId = null;
        moduleId = null;
        moduleSlug = null;
    }

    // ------------------------------------------------------------------ setup

    @Test
    @Order(1)
    void importFixturesAndCreateTwoUsers() throws Exception {
        importContentUseCase.execute(fixtureDocsPath());

        userId = register("srs+" + System.currentTimeMillis() + "@example.com");
        userToken = tokenService.generateAccessToken(userId, "USER");
        otherUserId = register("srs-other+" + System.currentTimeMillis() + "@example.com");
        otherUserToken = tokenService.generateAccessToken(otherUserId, "USER");

        MvcResult modules = mockMvc.perform(get("/modules").header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode first = objectMapper.readTree(body(modules)).get(0);
        moduleId = first.get("id").asText();
        moduleSlug = first.get("slug").asText();
        assertThat(moduleSlug).as("fixture sort theo displayOrder → module đầu là 01-java-core")
                .isEqualTo("01-java-core");
    }

    // ------------------------------------------------------------------ enroll → due

    @Test
    @Order(2)
    void enrollModuleCreatesCardsForEveryQuestionAndOneDeck() throws Exception {
        mockMvc.perform(post("/srs/enroll")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"moduleId":"%s"}
                                """.formatted(moduleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrolled").value(6))
                .andExpect(jsonPath("$.cardIds.length()").value(6))
                .andExpect(jsonPath("$.deckId").isNotEmpty());

        mockMvc.perform(post("/srs/enroll")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"moduleId":"%s"}
                                """.formatted(moduleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrolled").value(0))
                .andExpect(jsonPath("$.cardIds").isEmpty());

        // Criterion 4 cần cả hai vế: không thẻ trùng (UK) **và** không deck trùng (guard ở
        // application — V004 không có UK `(user_id, module_id)`). `enrolled: 0` chỉ chứng minh vế
        // thẻ; đếm deck thẳng trong DB mới chứng minh vế còn lại.
        assertThat(deckCountForUser(userId)).as("re-enroll cùng module không tạo deck thứ hai").isEqualTo(1);
    }

    @Test
    @Order(3)
    void dueReturnsEnrolledCardsWithContent() throws Exception {
        MvcResult due = mockMvc.perform(get("/srs/due").param("moduleId", moduleId).param("limit", "3")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").isNotEmpty())
                .andExpect(jsonPath("$[0].answerHtml").isNotEmpty())
                .andExpect(jsonPath("$[0].moduleSlug").value(moduleSlug))
                .andExpect(jsonPath("$[0].repetitions").value(0))
                .andExpect(jsonPath("$[0].easeFactor").value(2.5))
                .andReturn();

        assertThat(json(due, "$[0].cardId")).isNotBlank();
    }

    /** `moduleId` filter chạy trước limit ở application — xem `QueryDueUseCase`. */
    @Test
    @Order(4)
    void dueLimitIsCappedAndModuleFilterStillReturnsSomething() throws Exception {
        mockMvc.perform(get("/srs/due").param("limit", "99999").param("moduleId", moduleId)
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
    }

    // ------------------------------------------------------------------ review + attempt row

    @Test
    @Order(5)
    void reviewGoodUpdatesScheduleAndWritesFlashcardAttempt() throws Exception {
        String cardId = anyDueCardId();

        mockMvc.perform(post("/srs/review/{cardId}", cardId)
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quality":2,"timeMs":4200}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value(cardId))
                .andExpect(jsonPath("$.intervalDays").value(1))
                .andExpect(jsonPath("$.easeFactor").value(2.5))
                .andExpect(jsonPath("$.repetitions").value(1))
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.nextReview").value(LocalDate.now().plusDays(1).toString()));

        var attempts = attemptsFor(questionIdOf(cardId));
        assertThat(attempts).as("review phải ghi đúng 1 row study_attempts").hasSize(1);
        assertThat(attempts.get(0).source()).isEqualTo("FLASHCARD");
        assertThat(attempts.get(0).correct()).as("Good → đúng").isTrue();
        assertThat(attempts.get(0).timeMs()).isEqualTo(4200);
        assertThat(attempts.get(0).answer()).isNull();
    }

    @Test
    @Order(6)
    void reviewHardIsRecordedAsIncorrect() throws Exception {
        String cardId = anyDueCardId();

        mockMvc.perform(post("/srs/review/{cardId}", cardId)
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quality":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.repetitions").value(1));

        var attempts = attemptsFor(questionIdOf(cardId));
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).correct()).as("Hard → chưa nhớ").isFalse();
        assertThat(attempts.get(0).timeMs()).as("timeMs không gửi → NULL, không phải 0").isNull();
    }

    /** Sau khi ôn, thẻ không còn trong `due` hôm nay → phiên học co lại. */
    @Test
    @Order(7)
    void reviewedCardLeavesTheDueList() throws Exception {
        mockMvc.perform(get("/srs/due").param("moduleId", moduleId).param("limit", "100")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    // ------------------------------------------------------------------ mode B

    @Test
    @Order(8)
    void enrollByQuestionIdsWorksAndIsIsolatedPerUser() throws Exception {
        List<String> questionIds = dueCardQuestionIds(4);

        mockMvc.perform(post("/srs/enroll")
                        .header("Authorization", bearer(otherUserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionIds":["%s","%s","%s","%s"]}
                                """.formatted(questionIds.get(0), questionIds.get(1),
                                questionIds.get(2), questionIds.get(3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrolled").value(4))
                .andExpect(jsonPath("$.deckId").isEmpty());

        // User thứ hai thấy 4 thẻ của chính họ, không thấy thẻ đã ôn của user thứ nhất.
        mockMvc.perform(get("/srs/due").param("limit", "100")
                        .header("Authorization", bearer(otherUserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    @Order(9)
    void reviewOfAnotherUsersCardIsNotFound() throws Exception {
        String foreignCardId = anyDueCardId();

        mockMvc.perform(post("/srs/review/{cardId}", foreignCardId)
                        .header("Authorization", bearer(otherUserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quality":2}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("not_found"));
    }

    @Test
    @Order(10)
    void reviewWithoutAuthenticationIsRejected() throws Exception {
        String cardId = anyDueCardId();

        mockMvc.perform(post("/srs/review/{cardId}", cardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quality":2}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ validation

    @Test
    @Order(11)
    void enrollWithBothModesReturns400() throws Exception {
        mockMvc.perform(post("/srs/enroll")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"moduleId":"%s","questionIds":["%s"]}
                                """.formatted(moduleId, dueCardQuestionIds(1).get(0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("bad_request"));
    }

    @Test
    @Order(12)
    void enrollWithNeitherModeReturns400() throws Exception {
        mockMvc.perform(post("/srs/enroll")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("bad_request"));
    }

    @Test
    @Order(13)
    void enrollUnknownQuestionReturns404() throws Exception {
        mockMvc.perform(post("/srs/enroll")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionIds":["00000000-0000-0000-0000-000000000000"]}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("not_found"));
    }

    @Test
    @Order(14)
    void reviewQualityOutsideZeroToThreeReturns400() throws Exception {
        String cardId = anyDueCardId();

        mockMvc.perform(post("/srs/review/{cardId}", cardId)
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quality":4}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("validation_error"));

        mockMvc.perform(post("/srs/review/{cardId}", cardId)
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quality":-1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(15)
    void reviewUnknownCardReturns404() throws Exception {
        mockMvc.perform(post("/srs/review/{cardId}", "00000000-0000-0000-0000-000000000000")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quality":2}
                                """))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ concurrent review (M2)

    /**
     * Review phải **khoá row lúc đọc** (`findByIdForUpdate`), không chỉ đọc thường: `srs_cards`
     * không có cột `version` (V004), nên nếu review chỉ `SELECT` rồi mới `UPDATE`, hai review đồng
     * thời cùng thẻ đều đọc trạng thái cũ → một lần nhảy lịch bị ghi đè (lost update) dù cả hai đều
     * ghi attempt. Khi đó `repetitions` sai và SM-2 tính sai các lần sau.
     *
     * <p>Chứng minh: giữ khoá row bằng connection JDBC riêng, rồi gọi thẳng port
     * `findByIdForUpdate` trong một transaction khác. Có `SELECT ... FOR UPDATE` → call đứng chờ;
     * nếu chỉ `SELECT` thường → call trả về ngay và assert timeout đỏ.
     *
     * <p>Test qua port thay vì qua HTTP vì đây là hành vi của *lệnh đọc*: qua HTTP, cả bản có khoá
     * lẫn bản không khoá đều chặn ở `UPDATE`, nên không phân biệt được.
     */
    @Test
    @Order(17)
    void findByIdForUpdateBlocksWhileAnotherTransactionHoldsTheRowLock() throws Exception {
        String cardId = anyDueCardId();
        UUID cardUuid = UUID.fromString(cardId);

        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var started = new java.util.concurrent.CountDownLatch(1);
        try (var lockHolder = dataSource.getConnection()) {
            lockHolder.setAutoCommit(false);
            try (var statement = lockHolder.prepareStatement(
                    "SELECT id FROM srs_cards WHERE id = ? FOR UPDATE")) {
                statement.setObject(1, cardUuid);
                statement.executeQuery();
            }

            var future = executor.submit(() -> {
                started.countDown();
                return new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                        .execute(status -> srsCardRepository.findByIdForUpdate(cardUuid, userId).isPresent());
            });

            assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            // 1s >> thời gian 1 query local: không khoá thì future đã xong từ lâu.
            assertThatThrownBy(() -> future.get(1, java.util.concurrent.TimeUnit.SECONDS))
                    .as("findByIdForUpdate phải chờ khoá thay vì đọc xuyên qua")
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);

            lockHolder.rollback();
            assertThat(future.get(10, java.util.concurrent.TimeUnit.SECONDS))
                    .as("nhả khoá → đọc thấy thẻ, không phải lỗi")
                    .isEqualTo(Boolean.TRUE);
        } finally {
            executor.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ content deletion vs FK (C1)
    //
    // `srs_cards.question_id` và `study_attempts.question_id` là FK `NO ACTION` (V004): m5 là phase
    // đầu ghi row vào hai bảng đó, nên từ đây xoá câu hỏi (re-import hoặc admin xoá) sẽ nổ FK nếu
    // không dọn dependent trước. Hai test dưới cố định hành vi đó — trước khi fix, cả hai đỏ bằng
    // Postgres `violates foreign key constraint`.

    /** Admin xoá 1 câu đã có người enroll + ôn phải thành công, và dọn sạch thẻ/attempt của câu đó. */
    @Test
    @Order(18)
    void deletingEnrolledQuestionCleansDependentsInsteadOfFailingFk() throws Exception {
        UUID questionUuid = UUID.fromString(firstQuestionIdOfModule());
        mockMvc.perform(post("/srs/enroll")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionIds":["%s"]}
                                """.formatted(questionUuid)))
                .andExpect(status().isCreated());

        String cardId = cardIdFor(questionUuid);
        mockMvc.perform(post("/srs/review/{cardId}", cardId)
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quality":2,"timeMs":1000}
                                """))
                .andExpect(status().isOk());
        assertThat(attemptsFor(questionUuid)).as("tiền đề: có attempt trỏ tới câu này").isNotEmpty();

        // Không còn ném `DataIntegrityViolationException` nhờ `QuestionDependentsDao`.
        questionRepository.deleteById(questionUuid);

        assertThat(questionRepository.findById(questionUuid)).as("câu hỏi đã bị xoá").isEmpty();
        assertThat(attemptsFor(questionUuid)).as("attempt của câu bị xoá cũng bị dọn").isEmpty();
        assertThat(cardIdsFor(questionUuid)).as("thẻ SRS của câu bị xoá cũng bị dọn").isEmpty();
    }

    /**
     * Re-import xoá câu không còn trong docs cũng phải dọn dependent — đi qua đúng đường
     * `deleteAbsentSortOrders` mà `ImportContentUseCase` gọi (không cần sửa fixture).
     */
    @Test
    @Order(19)
    void reimportDeletingModuleQuestionsCleansDependents() throws Exception {
        UUID moduleUuid = UUID.fromString(moduleId);
        // Thẻ + attempt của các test trước vẫn trỏ tới câu trong module này.
        assertThat(cardIdsForModule(moduleUuid)).as("tiền đề: module còn thẻ SRS cần dọn").isNotEmpty();
        long questionsBefore = questionRepository.search(
                new QuestionQuery(moduleUuid, null, null, null, 1, QuestionQuery.MAX_SIZE))
                .totalElements();

        int deleted = questionRepository.deleteAbsentSortOrders(moduleUuid, List.of());

        assertThat((long) deleted).as("xoá toàn bộ câu còn lại của module").isEqualTo(questionsBefore);
        assertThat(cardIdsForModule(moduleUuid)).as("không còn thẻ mồ côi sau re-import").isEmpty();
    }

    /**
     * `questionIds: [null]` phải 400, không 500. Jackson deserialize null vào List&lt;UUID&gt;;
     * trước khi chặn tường minh ở `dedupe`, `List.copyOf` ném NPE → HTTP 500.
     */
    @Test
    @Order(20)
    void enrollWithNullQuestionIdElementReturns400() throws Exception {
        mockMvc.perform(post("/srs/enroll")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionIds":[null]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("bad_request"));
    }

    // ------------------------------------------------------------------ atomicity

    /**
     * Hai ghi của `ReviewCardUseCase` (update `srs_cards` + insert `study_attempts`) phải nằm trong
     * **một** transaction: insert nổ thì update cũng không được tồn tại.
     *
     * <p>Đây là quyết định kiến trúc quan trọng nhất của m5 (m7 cộng XP/mastery từ `study_attempts`),
     * và là thứ test "review ghi 1 row attempt" **không** chứng minh được — test đó chỉ phủ happy path.
     * Hậu quả nếu mất tính nguyên tử: lịch ôn nhảy trong khi tiến độ "đã ôn" biến mất, sai lệch âm
     * thầm không có gì sửa lại được.
     *
     * <p>Phạm vi chứng minh: nếu hai ghi nằm ở hai transaction riêng (update commit trước), card sẽ
     * đổi lịch ở lần đọc sau và assert `isEqualTo(before)` đỏ — nên test bắt được đúng lỗi "quên
     * `@Transactional`". Nó **không** phủ trường hợp update đã chạy xong rồi mới rollback, vì
     * Hibernate flush insert trước update: trigger `BEFORE INSERT` nổ khi câu UPDATE còn chưa gửi.
     *
     * <p>Trigger tạm được drop ngay trong `finally`: để sót lại thì mọi test sau trong cùng class
     * đỏ theo cách khó hiểu (và `@Order` chạy tuần tự nên cửa sổ này không ảnh hưởng test khác).
     */
    @Test
    @Order(16)
    void failedAttemptInsertRollsBackCardUpdate() throws Exception {
        String cardId = anyDueCardId();
        UUID cardUuid = UUID.fromString(cardId);
        UUID questionId = questionIdOf(cardId);
        CardRow before = cardRow(cardUuid);

        blockAttemptInserts();
        try {
            // MockMvc không có error-dispatch: exception ném ra khỏi handler bị tái ném thẳng thay vì
            // render 500. Bắt đúng exception mới chứng minh request đã fail; ngoài ra còn kiểm message
            // để chắc chắn nó fail vì trigger, không phải vì một lỗi khác vô tình trùng lúc.
            assertThatThrownBy(() -> mockMvc.perform(post("/srs/review/{cardId}", cardId)
                            .header("Authorization", bearer(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quality":2,"timeMs":3000}
                                    """)))
                    .as("insert study_attempts nổ → request phải fail, không được nuốt lỗi")
                    .isInstanceOf(ServletException.class)
                    .hasStackTraceContaining("study_attempts bị chặn");
        } finally {
            unblockAttemptInserts();
        }

        assertThat(cardRow(cardUuid))
                .as("attempt insert nổ → update card phải rollback, lịch ôn không được nhảy")
                .isEqualTo(before);
        assertThat(attemptsFor(questionId))
                .as("không attempt nào được ghi khi tx rollback")
                .isEmpty();
    }

    /** Ảnh chụp trạng thái lịch ôn — so sánh nguyên trạng để bắt mọi thay đổi lọt lưới. */
    private record CardRow(String intervalDays, String easeFactor, String nextReview,
                           String repetitions, String lastReviewedAt) {
    }

    private CardRow cardRow(UUID cardId) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT interval_days, ease_factor, next_review, repetitions, last_reviewed_at "
                             + "FROM srs_cards WHERE id = ?")) {
            statement.setObject(1, cardId);
            try (var rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new AssertionError("Không tìm thấy card " + cardId);
                }
                return new CardRow(rs.getString("interval_days"), rs.getString("ease_factor"),
                        rs.getString("next_review"), rs.getString("repetitions"),
                        rs.getString("last_reviewed_at"));
            }
        }
    }

    private void blockAttemptInserts() throws Exception {
        executeDdl("""
                CREATE FUNCTION kg_test_block_attempt() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'study_attempts bị chặn để test rollback';
                END;
                $$ LANGUAGE plpgsql;
                """);
        executeDdl("CREATE TRIGGER kg_test_block_attempt BEFORE INSERT ON study_attempts "
                + "FOR EACH ROW EXECUTE FUNCTION kg_test_block_attempt()");
    }

    private void unblockAttemptInserts() throws Exception {
        executeDdl("DROP TRIGGER IF EXISTS kg_test_block_attempt ON study_attempts");
        executeDdl("DROP FUNCTION IF EXISTS kg_test_block_attempt()");
    }

    private void executeDdl(String sql) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Đọc `study_attempts` bằng JDBC thuần thay vì repository JPA: `PresentationLayerArchTest`
     * cấm package `presentation` phụ thuộc `org.springframework.data.jpa.repository` (kể cả trong
     * test, vì ArchUnit import cả test classpath). JDBC ở đây cũng là điểm mạnh — dữ liệu được
     * đọc thẳng từ DB, không đi qua port ghi mà sản phẩm đang kiểm tra.
     */
    private record AttemptRow(String source, boolean correct, Integer timeMs, String answer) {
    }

    /**
     * Câu đầu tiên (theo `sort_order`) của module đang test — đọc qua port để test không phụ thuộc
     * thứ tự id. Dùng cho test xoá câu: cần một câu *cụ thể* đã enroll.
     */
    private String firstQuestionIdOfModule() {
        return questionRepository.search(
                        new QuestionQuery(UUID.fromString(moduleId), null, null, null, 1, 1))
                .items().get(0).getId().toString();
    }

    /** `srs_cards.id` của (user chính, câu hỏi) — thẻ vừa enroll. */
    private String cardIdFor(UUID questionId) throws Exception {
        List<UUID> ids = cardIdsFor(questionId);
        if (ids.isEmpty()) {
            throw new AssertionError("Không có thẻ SRS cho câu " + questionId);
        }
        return ids.get(0).toString();
    }

    private List<UUID> cardIdsFor(UUID questionId) throws Exception {
        return queryUuids("SELECT id FROM srs_cards WHERE question_id = ?", questionId);
    }

    private List<UUID> cardIdsForModule(UUID moduleId) throws Exception {
        return queryUuids("SELECT c.id FROM srs_cards c JOIN questions q ON q.id = c.question_id "
                + "WHERE q.module_id = ?", moduleId);
    }

    private List<UUID> queryUuids(String sql, UUID parameter) throws Exception {
        List<UUID> ids = new java.util.ArrayList<>();
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parameter);
            try (var rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add((UUID) rs.getObject(1));
                }
            }
        }
        return ids;
    }

    /** Số deck của user — chứng minh guard chống deck trùng ở tầng application (V004 không có UK). */
    private long deckCountForUser(UUID userId) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT count(*) FROM srs_decks WHERE user_id = ?")) {
            statement.setObject(1, userId);
            try (var rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private List<AttemptRow> attemptsFor(UUID questionId) throws Exception {
        List<AttemptRow> rows = new java.util.ArrayList<>();
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT source, is_correct, time_ms, answer FROM study_attempts "
                             + "WHERE user_id = ? AND question_id = ?")) {
            statement.setObject(1, userId);
            statement.setObject(2, questionId);
            try (var rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new AttemptRow(rs.getString("source"), rs.getBoolean("is_correct"),
                            (Integer) rs.getObject("time_ms"), rs.getString("answer")));
                }
            }
        }
        return rows;
    }

    /** Thẻ đến hạn đầu tiên của user chính — dùng cho các test review. */
    private String anyDueCardId() throws Exception {
        MvcResult due = mockMvc.perform(get("/srs/due").param("limit", "1")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();
        return json(due, "$[0].cardId");
    }

    private List<String> dueCardQuestionIds(int count) throws Exception {
        MvcResult due = mockMvc.perform(get("/srs/due").param("limit", String.valueOf(count))
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(count))
                .andReturn();
        JsonNode array = objectMapper.readTree(body(due));
        return java.util.stream.StreamSupport.stream(array.spliterator(), false)
                .map(node -> node.get("questionId").asText())
                .toList();
    }

    /**
     * Lấy `question_id` của thẻ thẳng từ DB. Không đọc từ `/srs/due` vì thẻ vừa ôn đã bị đẩy ra
     * khỏi danh sách đến hạn — đúng hành vi sản phẩm, nhưng không dùng làm nguồn để assert row
     * attempt được.
     */
    private UUID questionIdOf(String cardId) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT question_id FROM srs_cards WHERE id = ?")) {
            statement.setObject(1, UUID.fromString(cardId));
            try (var rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new AssertionError("Không tìm thấy card " + cardId + " trong srs_cards");
                }
                return (UUID) rs.getObject("question_id");
            }
        }
    }

    private UUID register(String email) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"superSecret123","displayName":"SRS Tester"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmail(email).orElseThrow();
        return user.getId();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * `getContentAsString()` không tham số dùng ISO-8859-1 → tiếng Việt trong body thành mojibake
     * và assert trên nội dung sẽ sai theo cách khó thấy (giống `ContentApiIntegrationTest`).
     */
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
