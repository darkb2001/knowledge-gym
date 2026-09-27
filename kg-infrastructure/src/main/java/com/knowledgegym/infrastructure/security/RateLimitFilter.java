package com.knowledgegym.infrastructure.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Rate limit filter — Bucket4j Redis-backed CAS tokens.
 * Áp dụng cho: login 5/min, register 10/h, forgot-password 3/min, reset-password 5/min, global 100/min/IP.
 * Key: rate:{endpoint}:{clientIp}
 * X-Forwarded-For chỉ tin khi app.security.trust-forwarded-headers=true (sau nginx).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final LettuceBasedProxyManager<String> proxyManager;
    private final boolean trustForwardedHeaders;
    private final Map<String, BucketConfiguration> configCache = new ConcurrentHashMap<>();

    private static final Map<String, Supplier<BucketConfiguration>> ENDPOINT_LIMITS = Map.of(
            "/auth/login", () -> BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofMinutes(1)).build()).build(),
            "/auth/register", () -> BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofHours(1)).build()).build(),
            "/auth/forgot-password", () -> BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder().capacity(3).refillGreedy(3, Duration.ofMinutes(1)).build()).build(),
            "/auth/reset-password", () -> BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofMinutes(1)).build()).build()
    );

    private static final Supplier<BucketConfiguration> GLOBAL_LIMIT = () -> BucketConfiguration.builder()
            .addLimit(Bandwidth.builder().capacity(100).refillGreedy(100, Duration.ofMinutes(1)).build()).build();

    private static final Set<String> ENDPOINT_LIMIT_PATHS =
            Set.of("/auth/login", "/auth/register", "/auth/forgot-password", "/auth/reset-password");

    public RateLimitFilter(LettuceBasedProxyManager<String> proxyManager,
                            @Value("${app.security.trust-forwarded-headers:false}") boolean trustForwardedHeaders) {
        this.proxyManager = proxyManager;
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String clientIp = getClientIp(request);
        String rateLimitKey = "rate:" + path + ":" + clientIp;

        // Cache config theo path only — tránh ConcurrentHashMap unbounded theo IP
        BucketConfiguration config = configCache.computeIfAbsent(path, p -> {
            Supplier<BucketConfiguration> supplier = ENDPOINT_LIMITS.get(p);
            return (supplier != null ? supplier : GLOBAL_LIMIT).get();
        });

        Bucket bucket = proxyManager.builder().build(rateLimitKey, config);

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            long retryAfterSeconds = 60L;
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(retryAfterSeconds));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"error":"rate_limit_exceeded","message":"Too many requests. Try again later.","retryAfter":%d}
                    """.formatted(retryAfterSeconds));
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !ENDPOINT_LIMIT_PATHS.contains(path) && !path.startsWith("/auth/");
    }

    private String getClientIp(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isEmpty()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}