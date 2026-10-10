package com.knowledgegym.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Defense-in-depth cho CSRF khi {@code SecurityConfig} tắt {@code CsrfFilter}.
 *
 * <p>Bối cảnh: mutation nghiệp vụ xác thực bằng Bearer access token trong bộ nhớ nên không CSRF
 * được. Chỉ {@code /auth/refresh} và {@code /auth/logout} dựa vào refresh cookie HttpOnly. Cookie
 * đã có {@code SameSite=Strict} + host-only, nhưng nếu lớp đó bị nới (thêm subdomain khác
 * registrable domain, đổi sang Lax/None, hoặc trình duyệt cũ) thì không còn gì chặn cross-site.
 *
 * <p>Filter này thêm một điều kiện độc lập: mọi request mutating **có gửi refresh cookie** phải
 * mang {@code Origin} (hoặc, khi browser không gửi Origin, {@code Referer}) thuộc allowlist.
 * Không có credential cookie thì không cần kiểm tra — request cross-site không kèm cookie vẫn
 * vô hại.
 *
 * <p>Không áp cho {@code /actuator/**} và {@code /internal/**}: các endpoint đó dùng token riêng,
 * không phải cookie của browser.
 */
@Component
public class OriginGuardFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OriginGuardFilter.class);
    private static final String COOKIE_NAME = "refreshToken";
    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final List<String> allowedOrigins;
    private final boolean enabled;

    public OriginGuardFilter(
            @Value("${app.security.cors.allowed-origins:}") String origins,
            @Value("${app.security.csrf.origin-guard:true}") boolean enabled) {
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .map(s -> s.endsWith("/") ? s.substring(0, s.length() - 1) : s)
                .filter(s -> !s.isEmpty())
                .toList();
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled || allowedOrigins.isEmpty()) return true;
        if (!MUTATING.contains(request.getMethod())) return true;
        if (!hasRefreshCookie(request)) return true;
        String path = request.getRequestURI();
        return path.startsWith("/actuator/") || path.contains("/actuator/")
                || path.startsWith("/internal/") || path.contains("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isSameSiteAllowed(request)) {
            log.warn("Rejected cookie-bearing {} {} — untrusted Origin/Referer",
                    request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"forbidden\",\"message\":\"Cross-site request rejected\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Origin được ưu tiên; chỉ khi browser không gửi Origin mới suy từ Referer. */
    private boolean isSameSiteAllowed(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            return allowedOrigins.contains(normalize(origin));
        }
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            // Không có Origin lẫn Referer trên request mang cookie: không thể chứng minh same-site.
            return false;
        }
        return refererMatchesAllowedOrigin(referer);
    }

    /**
     * Referer phải TRÙNG origin allowlist hoặc có origin đó làm prefix kết thúc bằng dấu {@code /}.
     *
     * <p>Không dùng {@code startsWith} trần: allowlist {@code https://app.example.vn} sẽ khớp nhầm
     * {@code https://app.example.vn.evil.com/...} (tên miền khác, cùng tiền tố chuỗi) — một bypass
     * cross-site thật. Ranh giới {@code /} buộc Referer phải nằm trong origin allowlist.
     */
    private boolean refererMatchesAllowedOrigin(String referer) {
        String trimmed = referer.trim();
        for (String allowed : allowedOrigins) {
            if (trimmed.equals(allowed) || trimmed.startsWith(allowed + "/")) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String origin) {
        String trimmed = origin.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static boolean hasRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return false;
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return true;
            }
        }
        return false;
    }
}
