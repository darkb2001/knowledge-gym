package com.knowledgegym.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Defense-in-depth cho CSRF: request mutating mang refresh cookie phải có Origin/Referer hợp lệ.
 *
 * <p>Mutation dùng Bearer nên không CSRF được; filter này bảo vệ đúng hai endpoint dựa cookie
 * ({@code /auth/refresh}, {@code /auth/logout}) và không chạm tới request không mang credential.
 */
class OriginGuardFilterTest {

    private final String allowed = "https://app.example.vn";

    private OriginGuardFilter filter(boolean enabled) {
        return new OriginGuardFilter(allowed, enabled);
    }

    private MockHttpServletRequest cookieRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setCookies(new Cookie("refreshToken", "raw-token"));
        return request;
    }

    private boolean runs(MockHttpServletRequest request, OriginGuardFilter filter) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        return response.getStatus() != 403;
    }

    @Test
    void allowedOriginPassesThrough() throws Exception {
        MockHttpServletRequest request = cookieRequest("POST", "/auth/refresh");
        request.addHeader("Origin", allowed);

        assertThat(runs(request, filter(true))).isTrue();
    }

    @Test
    void trailingSlashAndCaseOnOriginAreNormalized() throws Exception {
        MockHttpServletRequest request = cookieRequest("POST", "/auth/refresh");
        request.addHeader("Origin", allowed + "/");

        assertThat(runs(request, filter(true))).isTrue();
    }

    @Test
    void foreignOriginIsRejected() throws Exception {
        MockHttpServletRequest request = cookieRequest("POST", "/auth/refresh");
        request.addHeader("Origin", "https://evil.example");

        assertThat(runs(request, filter(true))).isFalse();
    }

    @Test
    void missingOriginAndRefererIsRejectedForCookieRequests() throws Exception {
        assertThat(runs(cookieRequest("POST", "/auth/refresh"), filter(true))).isFalse();
        assertThat(runs(cookieRequest("DELETE", "/auth/logout"), filter(true))).isFalse();
    }

    @Test
    void refererIsUsedWhenBrowserOmitsOrigin() throws Exception {
        MockHttpServletRequest request = cookieRequest("POST", "/auth/refresh");
        request.addHeader("Referer", allowed + "/profile");

        assertThat(runs(request, filter(true))).isTrue();
    }

    @Test
    void foreignRefererIsRejected() throws Exception {
        MockHttpServletRequest request = cookieRequest("POST", "/auth/refresh");
        request.addHeader("Referer", "https://evil.example/attack");

        assertThat(runs(request, filter(true))).isFalse();
    }

    @Test
    void refererSharingTheAllowedPrefixButADifferentHostIsRejected() throws Exception {
        // allowlist https://app.example.vn không được khớp app.example.vn.evil.com (startsWith trần).
        MockHttpServletRequest request = cookieRequest("POST", "/auth/refresh");
        request.addHeader("Referer", allowed + ".evil.example/profile");

        assertThat(runs(request, filter(true))).isFalse();
    }

    @Test
    void originSharingTheAllowedPrefixButADifferentHostIsRejected() throws Exception {
        MockHttpServletRequest request = cookieRequest("POST", "/auth/refresh");
        request.addHeader("Origin", allowed + ".evil.example");

        assertThat(runs(request, filter(true))).isFalse();
    }

    @Test
    void requestsWithoutRefreshCookieAreNotInspected() throws Exception {
        // Login/register không có cookie và chạy cross-site first-load: không được chặn.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.addHeader("Origin", "https://evil.example");

        assertThat(runs(request, filter(true))).isTrue();
    }

    @Test
    void safeMethodsAndInternalPathsBypassTheGuard() throws Exception {
        MockHttpServletRequest getRequest = cookieRequest("GET", "/auth/sessions");
        getRequest.addHeader("Origin", "https://evil.example");
        assertThat(runs(getRequest, filter(true))).isTrue();

        MockHttpServletRequest internal = cookieRequest("POST", "/api/v1/internal/collect");
        internal.addHeader("Origin", "https://evil.example");
        assertThat(runs(internal, filter(true))).isTrue();

        MockHttpServletRequest prometheus = cookieRequest("POST", "/api/v1/actuator/prometheus");
        prometheus.addHeader("Origin", "https://evil.example");
        assertThat(runs(prometheus, filter(true))).isTrue();
    }

    @Test
    void disabledFlagTurnsTheGuardOff() throws Exception {
        MockHttpServletRequest request = cookieRequest("POST", "/auth/refresh");
        request.addHeader("Origin", "https://evil.example");

        assertThat(runs(request, filter(false))).isTrue();
    }

    @Test
    void emptyAllowlistDisablesTheGuardInsteadOfBlockingEverything() throws Exception {
        // CORS_ALLOWED_ORIGINS rỗng (dev chưa cấu hình) không được làm chết luồng auth.
        OriginGuardFilter unconfigured = new OriginGuardFilter("  ,", true);
        MockHttpServletRequest request = cookieRequest("POST", "/auth/refresh");

        assertThat(runs(request, unconfigured)).isTrue();
    }
}
