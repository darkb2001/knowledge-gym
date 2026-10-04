package com.knowledgegym.infrastructure.security;

import com.knowledgegym.shared.domain.model.UserRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    private static final String SECRET = "test-only-access-secret-at-least-32-bytes-long";
    private final UUID user = UUID.randomUUID();
    private final JdbcAccessTokenGuard guard = mock(JdbcAccessTokenGuard.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET, guard);
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void authorityComesFromDatabaseNotStaleJwtAdminClaim() throws Exception {
        when(guard.authorizedRole(eq(user), any(), anyBoolean())).thenReturn(Optional.of(UserRole.USER));
        var response = new MockHttpServletResponse();
        filter.doFilter(request(token("ADMIN", SECRET)), response, (req, res) -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertEquals(user, auth.getPrincipal());
            assertEquals("ROLE_USER", auth.getAuthorities().iterator().next().getAuthority());
        });
        assertEquals(200, response.getStatus());
    }
    @Test void blockedOrRevokedTokenStopsChainWith401() throws Exception {
        when(guard.authorizedRole(eq(user), any(), anyBoolean())).thenReturn(Optional.empty());
        var response = new MockHttpServletResponse();
        filter.doFilter(request(token("ADMIN", SECRET)), response, (req, res) -> fail("Must not call handler"));
        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void invalidSignatureNeverQueriesDatabaseOrAuthenticates() throws Exception {
        filter.doFilter(request(token("ADMIN", SECRET + "wrong")), new MockHttpServletResponse(), (req, res) ->
                assertNull(SecurityContextHolder.getContext().getAuthentication()));
        verifyNoInteractions(guard);
    }
    @Test void missingBearerDoesNotQueryDatabase() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) ->
                assertNull(SecurityContextHolder.getContext().getAuthentication()));
        verifyNoInteractions(guard);
    }
    private String token(String role, String secret) {
        return Jwts.builder().subject(user.toString()).claim("role", role)
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }
    private static MockHttpServletRequest request(String token) {
        var request = new MockHttpServletRequest("GET", "/admin/users");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
