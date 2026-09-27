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
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final SecretKey accessKey;

    public JwtAuthenticationFilter(@Value("${app.security.jwt.access-secret}") String secret) {
        this.accessKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
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
                String role = claims.get(JwtTokenService.CLAIM_ROLE, String.class);
                UserRole roleEnum = UserRole.valueOf(role);

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