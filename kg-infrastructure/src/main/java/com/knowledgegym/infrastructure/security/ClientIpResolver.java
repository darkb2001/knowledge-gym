package com.knowledgegym.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolve client IP thống nhất — X-Forwarded-For chỉ tin khi
 * {@code app.security.trust-forwarded-headers=true} (sau nginx/prod).
 * Dùng chung AuthController, RateLimitFilter, OAuth2SuccessHandler.
 */
@Component
public class ClientIpResolver {

    private final boolean trustForwardedHeaders;

    public ClientIpResolver(
            @Value("${app.security.trust-forwarded-headers:false}") boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    public String resolve(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    /** Truncate User-Agent để lưu audit (tránh cột quá dài). */
    public String userAgent(HttpServletRequest request, int maxLen) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) return null;
        return ua.length() > maxLen ? ua.substring(0, maxLen) : ua;
    }
}