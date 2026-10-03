package com.knowledgegym.infrastructure.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 * Key: rate:{endpoint}:{clientIp} (IP qua {@link ClientIpResolver}).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final LettuceBasedProxyManager<String> proxyManager;
    private final ClientIpResolver clientIpResolver;
    private final Map<String, BucketConfiguration> configCache = new ConcurrentHashMap<>();

    private static final Map<String, Supplier<BucketConfiguration>> ENDPOINT_LIMITS = Map.of(
            "/auth/login", () -> BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofMinutes(1)).build()).build(),
            "/auth/register", () -> BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofHours(1)).build()).build(),
            "/auth/forgot-password", () -> BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder().capacity(3).refillGreedy(3, Duration.ofMinutes(1)).build()).build(),
            "/auth/email-verification/request", () -> BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder().capacity(3).refillGreedy(3, Duration.ofMinutes(1)).build()).build(),
            "/auth/verify-email", () -> BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofMinutes(1)).build()).build(),
            "/auth/reset-password", () -> BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofMinutes(1)).build()).build()
    );

    private static final Supplier<BucketConfiguration> GLOBAL_LIMIT = () -> BucketConfiguration.builder()
            .addLimit(Bandwidth.builder().capacity(100).refillGreedy(100, Duration.ofMinutes(1)).build()).build();

    private static final Set<String> ENDPOINT_LIMIT_PATHS =
            Set.of("/auth/login", "/auth/register", "/auth/forgot-password", "/auth/reset-password",
                    "/auth/email-verification/request", "/auth/verify-email");

    public RateLimitFilter(LettuceBasedProxyManager<String> proxyManager, ClientIpResolver clientIpResolver) {
        this.proxyManager = proxyManager;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String path = requestPath(request);
        String clientIp = clientIpResolver.resolve(request);
        String rateLimitKey = "rate:" + path + ":" + clientIp;

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
        String path = requestPath(request);
        return !ENDPOINT_LIMIT_PATHS.contains(path) && !path.startsWith("/auth/");
    }

    /**
     * `getRequestURI()` bao gồm `server.servlet.context-path` (`/api/v1`), nên so khớp trực tiếp
     * với `/auth/login` sẽ **luôn trượt** → rate limit im lặng không chạy ở production.
     * `getServletPath()` đã trừ context-path nên khớp được cả khi có và không có context-path.
     */
    private static String requestPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isEmpty()) {
            return servletPath;
        }
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (uri != null && contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri == null ? "/" : uri;
    }
}