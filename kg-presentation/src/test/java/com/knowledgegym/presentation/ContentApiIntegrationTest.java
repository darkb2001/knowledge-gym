package com.knowledgegym.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegym.content.application.ImportContentUseCase;
import com.knowledgegym.content.domain.port.ContentImportJob;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.TokenService;
import com.knowledgegym.identity.domain.port.UserRepository;
import com.knowledgegym.shared.domain.model.UserRole;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * m4a end-to-end: content import (idempotent + sanitize), REST đọc, full-text search,
 * cache eviction, RBAC, và context-path `/api/v1`.
 *
 * Fixture `docs-mini/` = 4 module (13 câu) copy cấu trúc docs/ thật, kèm ca XSS + card thiếu
 * badge. Assert bằng **số cụ thể** (3 topic / 4 module / 13 câu), không phải "đủ dùng".
 *
 * Chạy theo thứ tự: import trước, các test đọc sau — dùng chung 1 context/DB.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContentApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("knowledgegym_content")
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
        registry.add("app.content.docs-path", ContentApiIntegrationTest::fixtureDocsPath);
    }

    private static String fixtureDocsPath() {
        URL url = ContentApiIntegrationTest.class.getResource("/fixtures/docs-mini");
        assertThat(url).as("fixture docs-mini phải có trong test resources").isNotNull();
        return Path.of(url.getPath()).toString();
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestRestTemplate restTemplate;
    @Autowired ImportContentUseCase importContentUseCase;
    @Autowired ContentImportJob contentImportJob;
    @Autowired UserRepository userRepository;
    @Autowired TokenService tokenService;
    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    int localServerPort;

    private static String adminToken;
    private static String userToken;
    private static String firstQuestionId;

    @BeforeAll
    static void resetStaticState() {
        adminToken = null;
        userToken = null;
        firstQuestionId = null;
    }

    // ------------------------------------------------------------------ import

    @Test
    @Order(1)
    void importParsesFixtureWithExactCounts() {
        ImportContentUseCase.ImportResult result = importContentUseCase.execute(fixtureDocsPath());

        assertThat(result.topics()).isEqualTo(3);
        assertThat(result.modules()).isEqualTo(4);
        assertThat(result.questions()).isEqualTo(13);
        assertThat(result.orphanModuleSlugs()).isEmpty();
    }

    @Test
    @Order(2)
    void reimportIsIdempotent() throws Exception {
        // Chạy lần 2 trên cùng docs path — unique (module_id, sort_order) + upsert.
        ImportContentUseCase.ImportResult second = importContentUseCase.execute(fixtureDocsPath());
        assertThat(second.questions()).isEqualTo(13);

        long total = countQuestionsViaApi();
        assertThat(total).as("re-import không được nhân đôi dữ liệu").isEqualTo(13);
    }

    // ------------------------------------------------------------------ public read

    @Test
    @Order(3)
    void topicsAndModulesAreExposed() throws Exception {
        mockMvc.perform(get("/topics").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].slug").value("foundation"))
                .andExpect(jsonPath("$[0].moduleCount").value(2));

        mockMvc.perform(get("/modules").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].slug").value("01-java-core"))
                .andExpect(jsonPath("$[0].questionCount").value(6));
    }

    @Test
    @Order(4)
    void questionListIsPagedAndFilterable() throws Exception {
        MvcResult page1 = mockMvc.perform(get("/questions")
                        .param("page", "1").param("size", "5")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(13))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andReturn();

        firstQuestionId = json(page1, "$.items[0].id");

        // moduleId filter → đúng 2 câu của 02-multithreading
        String moduleId = json(page1, "$.items[0].moduleId");
        mockMvc.perform(get("/questions")
                        .param("moduleId", moduleId).param("size", "100")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6));

        // difficulty filter — sai chính tả phải 400, không được im lặng bỏ filter
        mockMvc.perform(get("/questions").param("difficulty", "super-senior")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("bad_request"));
    }

    @Test
    @Order(5)
    void fullTextSearchMatchesAnswerAndVietnameseSynonym() throws Exception {
        // "heap" chỉ có trong answer/searchable_text, không có trong title.
        mockMvc.perform(get("/questions").param("q", "heap")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)));

        // Người học gõ không dấu "bo nho" → khớp nhờ token bỏ dấu trong searchable_text.
        mockMvc.perform(get("/questions").param("q", "bo nho")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)));

        // Từ không tồn tại → 0 hit (không được trả toàn bộ).
        mockMvc.perform(get("/questions").param("q", "zzzznonexistent")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @Order(6)
    void questionDetailDoesNotLeakQuizAnswerFlags() throws Exception {
        // m6 import sinh options thật; DTO public vẫn không lộ cờ đúng.
        MvcResult detail = mockMvc.perform(get("/questions/{id}", firstQuestionId)
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstQuestionId))
                .andExpect(jsonPath("$.options").isNotEmpty())
                .andReturn();

        String body = detail.getResponse().getContentAsString();
        assertThat(body).doesNotContain("isCorrect", "correct\",\"true");
    }

    @Test
    @Order(7)
    void unknownQuestionReturns404ProblemDetails() throws Exception {
        mockMvc.perform(get("/questions/{id}", "00000000-0000-0000-0000-000000000000")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("not_found"));
    }

    // ------------------------------------------------------------------ RBAC + cache

    @Test
    @Order(8)
    void adminEndpointRejectsUserWith403() throws Exception {
        mockMvc.perform(post("/admin/content/parse")
                        .header("Authorization", bearer(userToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("forbidden"));
    }

    @Test
    @Order(9)
    void adminMutationEvictsQuestionCache() throws Exception {
        String original = json(mockMvc.perform(get("/questions/{id}", firstQuestionId)
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk()).andReturn(), "$.title");

        String updatedTitle = original + " (đã sửa)";
        mockMvc.perform(patch("/admin/content/questions/{id}", firstQuestionId)
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s"}
                                """.formatted(updatedTitle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(updatedTitle));

        // Nếu cache không bị evict, response vẫn là title cũ.
        mockMvc.perform(get("/questions/{id}", firstQuestionId)
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(updatedTitle));

        // Trả về nguyên trạng để không phụ thuộc thứ tự test khác.
        mockMvc.perform(patch("/admin/content/questions/{id}", firstQuestionId)
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s"}
                                """.formatted(original)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(10)
    void adminParseEndpointReturns202WithJobId() throws Exception {
        MvcResult accepted = mockMvc.perform(post("/admin/content/parse")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").exists())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andReturn();

        UUID jobId = UUID.fromString(json(accepted, "$.jobId"));
        // Đợi job xong trước khi Orders 14–16 đụng DB/cache — tránh race upsert/clear.
        awaitJobSucceeded(jobId);
        assertThat(countQuestionsViaApi()).isEqualTo(13);

        // GET /jobs/{id} phải đọc được trạng thái (đóng gap: jobId vô hình trước đây).
        mockMvc.perform(get("/admin/content/jobs/{id}", jobId)
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.result.questions").value(13));
    }

    @Test
    @Order(22)
    void adminJobEndpointReturns404ForUnknownIdAnd403ForUser() throws Exception {
        mockMvc.perform(get("/admin/content/jobs/{id}", "00000000-0000-0000-0000-000000000099")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("not_found"));

        mockMvc.perform(get("/admin/content/jobs/{id}", "00000000-0000-0000-0000-000000000099")
                        .header("Authorization", bearer(userToken())))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ sanitize + context path

    @Test
    @Order(11)
    void sanitizeRemovesScriptTagsFromPersistedAnswer() throws Exception {
        // Fixture 01-java-core có <script>alert('stored-xss-should-be-removed')</script> trong
        // qa-answer của card "So sánh String bằng == hay equals?".
        MvcResult search = mockMvc.perform(get("/questions").param("q", "string equals")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)))
                .andReturn();
        String questionId = json(search, "$.items[0].id");

        MvcResult detail = mockMvc.perform(get("/questions/{id}", questionId)
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andReturn();
        String answerHtml = json(detail, "$.answerHtml");

        assertThat(answerHtml).contains("equals");
        assertThat(answerHtml).as("<script> phải bị strip trước khi persist").doesNotContain("<script", "alert(");
    }

    /**
     * `TestRestTemplate` thật sự đi qua servlet container: Spring Boot đã gắn context-path vào
     * root URI của nó, nên chỉ cần path nội bộ. Để chứng minh context-path có hiệu lực, gọi thẳng
     * URL không có prefix bằng `RestTemplate` thuần → servlet container trả 404.
     */
    @Test
    @Order(12)
    void contextPathPrefixesEveryEndpoint() {
        assertThat(restTemplate.getForEntity("/actuator/health", String.class).getStatusCode().value())
                .as("TestRestTemplate đã tự gắn context-path").isEqualTo(200);

        var rawRest = new org.springframework.web.client.RestTemplate();
        // Mặc định RestTemplate ném exception khi 4xx — tắt để so sánh status code ở đây.
        rawRest.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(org.springframework.http.client.ClientHttpResponse response) {
            }
        });

        ResponseEntity<String> without = rawRest.getForEntity(
                "http://localhost:" + localServerPort + "/actuator/health", String.class);
        ResponseEntity<String> with = rawRest.getForEntity(
                "http://localhost:" + localServerPort + "/api/v1/actuator/health", String.class);

        assertThat(without.getStatusCode().value())
                .as("Không có context-path thì servlet container không map được endpoint").isEqualTo(404);
        assertThat(with.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @Order(13)
    void swaggerUiIsReachableWhenEnabled() throws Exception {
        // API docs JSON — nguồn dữ liệu của Swagger UI.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Knowledge Gym API"));

        // Trang UI thật (không chỉ docs JSON) — CSP phải cho inline script, nếu không trang trắng.
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ admin sanitize + invariants

    /**
     * Cùng invariant với import nhưng qua đường admin: `answerHtml` phải qua sanitizer.
     * Nếu chỉ sanitize ở import, admin có thể lưu `<script>` rồi `GET /questions/{id}` trả nguyên
     * văn — đây là stored XSS, không phải reflected.
     */
    @Test
    @Order(14)
    void adminCreateSanitizesAnswerHtml() throws Exception {
        MvcResult created = mockMvc.perform(post("/admin/content/questions")
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"moduleId":"%s","title":"Câu admin tạo","answerHtml":"<p>nội dung</p><script>alert('xss')</script>"}
                                """.formatted(anyModuleId())))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(json(created, "$.answerHtml"))
                .as("<script> từ admin phải bị strip trước khi persist")
                .doesNotContain("<script", "alert(")
                .contains("nội dung");

        deleteQuestion(json(created, "$.id"));
    }

    /**
     * `sortOrder` trùng phải là 409, không được upsert ghi đè câu hỏi sẵn có rồi trả 201 —
     * client sẽ tưởng đã tạo mới trong khi dữ liệu của người khác bị thay.
     */
    @Test
    @Order(15)
    void adminCreateWithTakenSortOrderReturns409() throws Exception {
        MvcResult existing = mockMvc.perform(get("/questions").param("size", "1")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk()).andReturn();
        JsonNode first = at(existing.getResponse().getContentAsString(), "$.items[0]");
        String moduleId = first.get("moduleId").asText();
        int sortOrder = first.get("sortOrder").asInt();

        mockMvc.perform(post("/admin/content/questions")
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"moduleId":"%s","title":"Đè lên câu cũ","answerHtml":"<p>x</p>","sortOrder":%d}
                                """.formatted(moduleId, sortOrder)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("conflict"));
    }

    /**
     * Sửa nội dung phải tính lại `searchKeywords`; nếu không, `searchable_text` giữ từ của bản cũ
     * và search trả về câu hỏi không còn chứa từ khoá đó.
     */
    @Test
    @Order(16)
    void adminUpdateRecomputesSearchKeywordsAndSearchReflectsIt() throws Exception {
        MvcResult created = mockMvc.perform(post("/admin/content/questions")
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"moduleId":"%s","title":"Câu để sửa index","answerHtml":"<p>zzmarkerone</p>"}
                                """.formatted(anyModuleId())))
                .andExpect(status().isCreated())
                .andReturn();
        String id = json(created, "$.id");

        mockMvc.perform(patch("/admin/content/questions/{id}", id)
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answerHtml":"<p>replication lag giữa các node</p>"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchKeywords", org.hamcrest.Matchers.hasItem("replication")));

        mockMvc.perform(get("/questions").param("q", "replication lag")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)));

        // Marker cũ không còn trong tài liệu → không được match nữa.
        mockMvc.perform(get("/questions").param("q", "zzmarkerone")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        deleteQuestion(id);
    }

    /**
     * Docs là source of truth: câu admin tạo thêm (sortOrder ngoài parse) phải biến mất
     * khi re-import cùng fixture — không để orphan `(module_id, sort_order)`.
     */
    @Test
    @Order(17)
    void reimportDeletesQuestionsAbsentFromDocs() throws Exception {
        MvcResult created = mockMvc.perform(post("/admin/content/questions")
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"moduleId":"%s","title":"Câu orphan sau reimport","answerHtml":"<p>stale</p>"}
                                """.formatted(anyModuleId())))
                .andExpect(status().isCreated())
                .andReturn();
        String orphanId = json(created, "$.id");
        assertThat(countQuestionsViaApi()).isEqualTo(14);

        ImportContentUseCase.ImportResult result = importContentUseCase.execute(fixtureDocsPath());
        assertThat(result.deleted()).isGreaterThanOrEqualTo(1);
        assertThat(countQuestionsViaApi()).as("re-import phải xoá câu không còn trong docs").isEqualTo(13);

        mockMvc.perform(get("/questions/{id}", orphanId)
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ regression: search + paging

    /**
     * Hồi quy BLOCKER: người học gõ "khong dong bo" (có hư từ "không", không dấu) phải ra kết quả.
     *
     * Trước fix trả về **0 hit** dù fixture có câu "Đồng bộ bất đồng bộ…": `plainto_tsquery('simple',
     * 'khong dong bo')` sinh `'khong' & 'dong' & 'bo'`, mà index không có token `khong` — dạng bỏ
     * dấu của "không" nằm trong STOPWORDS nên bị loại khỏi index.
     */
    @Test
    @Order(18)
    void vietnameseQueryWithStopwordAndNoDiacriticsStillMatches() throws Exception {
        mockMvc.perform(get("/questions").param("q", "khong dong bo")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)));

        // Cùng truy vấn nhưng gõ có dấu cũng phải khớp — người dùng không nhất quán.
        mockMvc.perform(get("/questions").param("q", "đồng bộ")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    /**
     * Query chỉ toàn hư từ ("là gì") không còn token nội dung nào. Chọn trả **toàn bộ** thay vì 0
     * hit: không filter (khác với việc bịa ra một điều kiện không match gì). Hành vi này được
     * assert tường minh để sau này đổi ý thì test phải đổi theo.
     */
    @Test
    @Order(19)
    void pureStopwordQueryBehavesAsNoFilter() throws Exception {
        mockMvc.perform(get("/questions").param("q", "là gì").param("size", "100")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(13));
    }

    /**
     * Hồi quy BLOCKER: `page=Integer.MAX_VALUE` từng làm `(page-1)*size` tràn `int` → offset âm →
     * Postgres từ chối → API trả **409 "Resource already exists"**, một message hoàn toàn không
     * liên quan tới tham số client gửi.
     */
    @Test
    @Order(20)
    void extremePageReturnsEmptyPageInsteadOfServerError() throws Exception {
        mockMvc.perform(get("/questions")
                        .param("page", String.valueOf(Integer.MAX_VALUE)).param("size", "20")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(13));
    }

    /** Tham số phân trang rác phải bị clamp về giá trị hợp lệ, không được 4xx/5xx. */
    @Test
    @Order(21)
    void invalidPagingParamsAreClampedNotRejected() throws Exception {
        mockMvc.perform(get("/questions").param("page", "0").param("size", "0")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20));

        mockMvc.perform(get("/questions").param("size", "99999")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    // ------------------------------------------------------------------ helpers

    private void awaitJobSucceeded(UUID jobId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            var snapshot = contentImportJob.find(jobId);
            if (snapshot.isPresent()
                    && snapshot.get().status() != ContentImportJob.Status.RUNNING) {
                assertThat(snapshot.get().status())
                        .as("job %s phải SUCCEEDED", jobId)
                        .isEqualTo(ContentImportJob.Status.SUCCEEDED);
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timeout đợi content import job " + jobId);
    }

    /** Module của câu hỏi đầu tiên — mọi test admin tạo/sửa đều cần một moduleId hợp lệ. */
    private String anyModuleId() throws Exception {
        MvcResult result = mockMvc.perform(get("/questions").param("size", "1")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andReturn();
        return json(result, "$.items[0].moduleId");
    }

    /** Dọn câu hỏi do test tạo để không làm lệch số đếm của các test chạy sau. */
    private void deleteQuestion(String id) throws Exception {
        mockMvc.perform(delete("/admin/content/questions/{id}", id)
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isNoContent());
    }

    private long countQuestionsViaApi() throws Exception {
        MvcResult result = mockMvc.perform(get("/questions").param("size", "100")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(body(result)).get("totalElements").asLong();
    }

    /**
     * Đọc body dưới dạng UTF-8.
     *
     * `getContentAsString()` không tham số dùng `characterEncoding` của response, mặc định
     * ISO-8859-1 → mọi tiếng Việt trong body thành mojibake. Test so khớp chuỗi tiếng Việt
     * (hoặc ghi lại giá trị đọc được) sẽ sai một cách khó thấy nếu dùng bản không tham số.
     */
    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Đăng ký user qua HTTP rồi tự nâng role để mint token ADMIN (không có endpoint promote). */
    private String adminToken() {
        if (adminToken == null) {
            userToken();
            String email = "admin+" + System.currentTimeMillis() + "@example.com";
            register(email);
            User user = userRepository.findByEmail(email).orElseThrow();
            user.setRole(UserRole.ADMIN);
            userRepository.save(user);
            adminToken = tokenService.generateAccessToken(user.getId(), UserRole.ADMIN.name());
        }
        return adminToken;
    }

    private String userToken() {
        if (userToken == null) {
            String email = "user+" + System.currentTimeMillis() + "@example.com";
            register(email);
            User user = userRepository.findByEmail(email).orElseThrow();
            userToken = tokenService.generateAccessToken(user.getId(), user.getRole().name());
        }
        return userToken;
    }

    private void register(String email) {
        try {
            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","password":"superSecret123","displayName":"Content Tester"}
                                    """.formatted(email)))
                    .andExpect(status().isCreated());
        } catch (Exception e) {
            throw new IllegalStateException("register failed for " + email, e);
        }
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * Đọc 1 field từ JSON response bằng path dạng `$.items[0].id`.
     * `JsonNode.at` chỉ nhận JSON Pointer (`/items/0/id`) nên đổi ở đây; call site giữ dạng
     * `$.a.b[0]` cho dễ đọc.
     */
    static JsonNode at(String body, String path) throws Exception {
        String pointer = path.startsWith("$") ? path.substring(1) : path;
        pointer = pointer.replaceAll("\\[(\\d+)\\]", "/$1").replace('.', '/');
        return new ObjectMapper().readTree(body).at(pointer);
    }

    private static String json(MvcResult result, String path) throws Exception {
        JsonNode node = at(body(result), path);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }
}
