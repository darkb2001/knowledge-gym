package com.knowledgegym.presentation.telemetry;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds the authenticated actor to the MDC so every log line written while the
 * request is served can be filtered by user without a JSON parse.
 *
 * <p>Filter order matters twice over. Spring Security registers its own chain at
 * {@code SecurityProperties.DEFAULT_FILTER_ORDER} (-100); {@link RequestTelemetryFilter}
 * runs before it at {@link Ordered#HIGHEST_PRECEDENCE}+10 and therefore cannot see
 * a principal. This filter sits at -90, inside the Security chain and after
 * {@code JwtAuthenticationFilter}, so the principal exists — and it stays inside
 * the request scope, so the completed-request line emitted by
 * {@link RequestTelemetryFilter} still carries the value.
 *
 * <p>Only the account UUID is published, never an email, name or token, and
 * {@code SafeJsonEncoder} re-validates the shape before writing the JSON field.
 * This filter deliberately does not clear the MDC: {@link RequestTelemetryFilter}
 * saves and restores the full context map around the whole chain, which is the
 * single place that guarantees no value leaks into the next request on the thread.
 */
@Component
@Order(-90)
public final class UserMdcFilter extends OncePerRequestFilter {
    static final String USER_ID = "user_id";

    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        // Never overwrite an actor already bound to this request (include,
        // forward or async redispatch) and never invent one.
        if (MDC.get(USER_ID) == null) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof UUID userId) {
                MDC.put(USER_ID, userId.toString());
            }
        }
        chain.doFilter(request, response);
    }
}
