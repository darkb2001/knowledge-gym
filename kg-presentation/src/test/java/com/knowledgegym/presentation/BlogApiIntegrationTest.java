package com.knowledgegym.presentation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Blog public API trên Postgres thật: envelope phân trang `GET /blog/posts` + tác giả comment.
 *
 * <p>Chứng minh những thứ unit test với fake không thấy được:
 * <ul>
 *   <li>`count(*)` + OFFSET/LIMIT (total/hasMore đúng khi chia trang; bài DRAFT bị loại).</li>
 *   <li>Comment list JOIN `users` → `authorDisplayName`/`authorAvatarUrl` có mặt (không N+1).</li>
 * </ul>
 */
@org.springframework.context.annotation.Import(TestEmailServiceConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BlogApiIntegrationTest {

    private static final String AVATAR = "https://cdn.example.com/blog-author.png";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("knowledgegym_blog")
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
    @Autowired UserRepository userRepository;
    @Autowired TokenService tokenService;
    @Autowired DataSource dataSource;

    private static String authorToken;
    private static UUID authorId;

    private org.springframework.jdbc.core.JdbcTemplate db() {
        return new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    }

    private JsonNode response(MockHttpServletRequestBuilder request, int expected) throws Exception {
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private MockHttpServletRequestBuilder postJson(String path, Object value, String token) throws Exception {
        return post(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(value));
    }

    @Test @Order(1) void setup() throws Exception {
        authorId = register("blog-author@example.com", "Blog Author");
        authorToken = tokenService.generateAccessToken(authorId, "USER");
        db().update("UPDATE users SET avatar_url=? WHERE id=?", AVATAR, authorId);

        insertPost("pg-pagination-a", Instant.now().minusSeconds(60));
        insertPost("pg-pagination-b", Instant.now().minusSeconds(120));
        insertPost("pg-pagination-c", Instant.now().minusSeconds(180));
        // DRAFT không được tính vào total.
        db().update("INSERT INTO blog_posts (author_type,title,slug,body,status,tags) VALUES ('HUMAN',?,?,?,'DRAFT','{}')",
                "Bản nháp", "pg-pagination-draft", "body");
    }

    private void insertPost(String slug, Instant publishedAt) {
        db().update("INSERT INTO blog_posts (author_type,title,slug,body,excerpt,status,published_at,tags) "
                        + "VALUES ('HUMAN',?,?,?,?,'PUBLISHED',?,'{}')",
                "Tiêu đề " + slug, slug, "<p>body</p>", "excerpt " + slug, Timestamp.from(publishedAt));
    }

    @Test @Order(2) void phanTrangTraEnvelopeDungTotalVaHasMore() throws Exception {
        // page 0, size 2 → 2 item, còn trang sau.
        var first = response(get("/blog/posts").param("page", "0").param("size", "2"), 200);
        assertThat(first.get("items").size()).isEqualTo(2);
        assertThat(first.get("total").asLong()).isEqualTo(3);
        assertThat(first.get("page").asInt()).isZero();
        assertThat(first.get("size").asInt()).isEqualTo(2);
        assertThat(first.get("hasMore").asBoolean()).isTrue();

        // page 1, size 2 → 1 item, hết.
        var second = response(get("/blog/posts").param("page", "1").param("size", "2"), 200);
        assertThat(second.get("items").size()).isEqualTo(1);
        assertThat(second.get("total").asLong()).isEqualTo(3);
        assertThat(second.get("page").asInt()).isEqualTo(1);
        assertThat(second.get("hasMore").asBoolean()).isFalse();

        // Không tham số: default page=0, size=10.
        var defaults = response(get("/blog/posts"), 200);
        assertThat(defaults.get("page").asInt()).isZero();
        assertThat(defaults.get("size").asInt()).isEqualTo(10);
        assertThat(defaults.get("total").asLong()).isEqualTo(3);
        assertThat(defaults.get("hasMore").asBoolean()).isFalse();

        // size bị cap ở 50.
        var capped = response(get("/blog/posts").param("size", "100"), 200);
        assertThat(capped.get("size").asInt()).isEqualTo(50);

        // items giữ nguyên field cũ của PostSummary.
        JsonNode item = first.get("items").get(0);
        assertThat(item.has("id")).isTrue();
        assertThat(item.has("title")).isTrue();
        assertThat(item.has("slug")).isTrue();
        assertThat(item.has("excerpt")).isTrue();
        assertThat(item.has("publishedAt")).isTrue();
        assertThat(item.has("viewCount")).isTrue();
        assertThat(item.has("likeCount")).isTrue();
        assertThat(item.has("tags")).isTrue();
        assertThat(item.has("body")).isFalse();
    }

    @Test @Order(3) void commentCreateTraTacGia() throws Exception {
        var created = response(postJson("/blog/posts/pg-pagination-a/comments",
                Map.of("content", "Bình luận hay"), authorToken), 201);

        assertThat(created.get("id").asText()).isNotBlank();
        assertThat(created.get("userId").asText()).isEqualTo(authorId.toString());
        assertThat(created.get("content").asText()).isEqualTo("Bình luận hay");
        assertThat(created.get("createdAt").isNull()).isFalse();
        assertThat(created.get("authorDisplayName").asText()).isEqualTo("Blog Author");
        assertThat(created.get("authorAvatarUrl").asText()).isEqualTo(AVATAR);
    }

    @Test @Order(4) void commentListTraTacGiaChoMoiComment() throws Exception {
        var comments = response(get("/blog/posts/pg-pagination-a/comments"), 200);

        assertThat(comments.size()).isEqualTo(1);
        JsonNode comment = comments.get(0);
        assertThat(comment.get("authorDisplayName").asText()).isEqualTo("Blog Author");
        assertThat(comment.get("authorAvatarUrl").asText()).isEqualTo(AVATAR);
        // Field cũ không bị đổi tên/loại bỏ.
        assertThat(comment.has("id")).isTrue();
        assertThat(comment.has("userId")).isTrue();
        assertThat(comment.has("parentId")).isTrue();
        assertThat(comment.has("content")).isTrue();
        assertThat(comment.has("createdAt")).isTrue();
    }

    private UUID register(String email, String displayName) throws Exception {
        String code = RegistrationTestSupport.code(mockMvc, email);
        response(postJson("/auth/register", Map.of("email", email, "password", "superSecret123",
                "confirmPassword", "superSecret123", "displayName", displayName, "verificationCode", code), "")
                .header("X-Forwarded-For", RegistrationTestSupport.nextIp()), 201);
        return userRepository.findByEmail(email).orElseThrow().getId();
    }
}
