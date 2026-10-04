package com.knowledgegym.infrastructure.security;

import com.knowledgegym.shared.domain.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final SecretKey accessKey;
    private final JdbcAccessTokenGuard accountGuard;

    public JwtAuthenticationFilter(@Value("${app.security.jwt.access-secret}") String secret,
                                   JdbcAccessTokenGuard accountGuard) {
        this.accessKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accountGuard = accountGuard;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length());
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(accessKey)
                        .clockSkewSeconds(30)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
                UUID userId = UUID.fromString(claims.getSubject());
                Instant issuedAt = claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant();
                Number issuedAtMillis = claims.get(JwtTokenService.CLAIM_ISSUED_AT_MILLIS, Number.class);
                if (issuedAtMillis != null) issuedAt = Instant.ofEpochMilli(issuedAtMillis.longValue());
                var role = accountGuard.authorizedRole(userId, issuedAt, issuedAtMillis != null);
                if (role.isEmpty()) {
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"Session invalidated\"}");
                    return;
                }
                UserRole roleEnum = role.get();

                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + roleEnum.name()));

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("Invalid JWT: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}