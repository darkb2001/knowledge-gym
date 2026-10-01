package com.knowledgegym.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegym.content.application.ImportContentUseCase;
import com.knowledgegym.identity.domain.port.TokenService;
import com.knowledgegym.identity.domain.port.UserRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * m8 notes + PG full-text search — ownership, export routing, convert idempotency.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotesApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("knowledgegym_notes")
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
        registry.add("app.content.docs-path", NotesApiIntegrationTest::fixtureDocsPath);
    }

    private static String fixtureDocsPath() {
        URL url = NotesApiIntegrationTest.class.getResource("/fixtures/docs-mini");
        assertThat(url).as("fixture docs-mini phải có trong test resources").isNotNull();
        return Path.of(url.getPath()).toString();
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ImportContentUseCase importContentUseCase;
    @Autowired UserRepository userRepository;
    @Autowired TokenService tokenService;
    @Autowired javax.sql.DataSource dataSource;

    private static String ownerToken;
    private static String otherToken;
    private static UUID ownerId;
    private static UUID questionId;
    private static UUID noteId;
    private static UUID bookmarkId;

    @Test
    @Order(1)
    void importAndRegisterUsers() throws Exception {
        importContentUseCase.execute(fixtureDocsPath());
        ownerId = register("notes-owner+" + System.currentTimeMillis() + "@example.com");
        ownerToken = tokenService.generateAccessToken(ownerId, "USER");
        UUID otherId = register("notes-other+" + System.currentTimeMillis() + "@example.com");
        otherToken = tokenService.generateAccessToken(otherId, "USER");
        questionId = firstQuestionId();
        assertThat(questionId).isNotNull();
    }

    @Test
    @Order(2)
    void createListGetUpdateAndRejectForeignAccess() throws Exception {
        MvcResult created = mockMvc.perform(post("/notes")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionId":"%s","noteType":"QUICK","content":"JDK = JRE + tools","tags":["java","jvm"]}
                                """.formatted(questionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.noteType").value("QUICK"))
                .andExpect(jsonPath("$.questionId").value(questionId.toString()))
                .andReturn();
        noteId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(get("/notes").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(noteId.toString()));

        mockMvc.perform(get("/notes/{id}", noteId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("JDK = JRE + tools"));

        mockMvc.perform(get("/notes/{id}", noteId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/notes/{id}", noteId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionId":"%s","noteType":"STUDY","content":"Updated note body","tags":["spring"]}
                                """.formatted(questionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteType").value("STUDY"))
                .andExpect(jsonPath("$.content").value("Updated note body"));

        mockMvc.perform(post("/notes")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"noteType":"QUICK","content":"x","questionId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    void searchExportBookmarksAndConvertAreIdempotent() throws Exception {
        MvcResult bookmark = mockMvc.perform(post("/notes")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionId":"%s","noteType":"BOOKMARK","content":"ôn lại replication","tags":["db"]}
                                """.formatted(questionId)))
                .andExpect(status().isCreated())
                .andReturn();
        bookmarkId = UUID.fromString(objectMapper.readTree(bookmark.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(get("/users/me/bookmarks").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(bookmarkId.toString()));

        mockMvc.perform(get("/search").param("q", "replication")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='note')]").isNotEmpty());

        mockMvc.perform(get("/notes/export").param("format", "md")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/markdown")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("# My notes")
                        .contains("Updated note body"));

        // Path-variable UUID constraint: /notes/export must not be treated as {id}
        mockMvc.perform(get("/notes/export").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        String firstCard = mockMvc.perform(post("/notes/{id}/convert", noteId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String secondCard = mockMvc.perform(post("/notes/{id}/convert", noteId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(firstCard).get("cardId").asText())
                .isEqualTo(objectMapper.readTree(secondCard).get("cardId").asText());

        mockMvc.perform(post("/notes/{id}/convert", bookmarkId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(4)
    void deleteIsOwnerScoped() throws Exception {
        mockMvc.perform(delete("/notes/{id}", noteId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/notes/{id}", noteId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/notes/{id}", noteId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
    }

    private UUID register(String email) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"superSecret123","displayName":"Notes Tester"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private UUID firstQuestionId() throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT id FROM questions ORDER BY sort_order LIMIT 1")) {
            assertThat(rs.next()).isTrue();
            return rs.getObject(1, UUID.class);
        }
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
