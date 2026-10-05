package com.knowledgegym.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Chặn bot ở các endpoint "tạo dữ liệu / gửi mail": đăng ký, gửi mã xác minh, quên mật khẩu.
 *
 * <p>Không đặt Turnstile ở `/auth/login`: người dùng thật bị chặn oan sẽ không đăng nhập được, trong khi
 * login đã có rate limit theo IP (app) + zone `auth` ở nginx.
 *
 * <p>Đọc token ở header `X-Turnstile-Token` (không nhét vào body) để không phải đổi DTO của 5 endpoint.
 */
@Component
public class TurnstileFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Turnstile-Token";

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/auth/register",
            "/auth/email-verification/request",
            "/auth/verify-email",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/password-code/request"
    );

    private final TurnstileVerifier verifier;
    private final ClientIpResolver clientIpResolver;

    public TurnstileFilter(TurnstileVerifier verifier, ClientIpResolver clientIpResolver) {
        this.verifier = verifier;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        if (verifier.verify(token, clientIpResolver.resolve(request))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"error":"turnstile_failed","message":"Xác minh chống bot không hợp lệ hoặc đã hết hạn. Tải lại trang và thử lại."}
                """);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!verifier.isEnabled()) {
            return true;
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !PROTECTED_PATHS.contains(requestPath(request));
    }

    /**
     * `getRequestURI()` chứa context-path (`/api/v1`) nên so khớp trực tiếp sẽ luôn trượt;
     * `getServletPath()` đã trừ context-path (giống {@link RateLimitFilter}).
     */
    private static String requestPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isEmpty()) {
            return servletPath;
        }
        String uri = request.getRequestURI();
        int idx = uri.indexOf("/auth/");
        return idx >= 0 ? uri.substring(idx) : uri;
    }
}
