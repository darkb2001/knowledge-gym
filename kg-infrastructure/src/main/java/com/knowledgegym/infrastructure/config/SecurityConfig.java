package com.knowledgegym.infrastructure.config;

import com.knowledgegym.infrastructure.security.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final TurnstileFilter turnstileFilter;
    private final OAuth2SuccessHandler oauth2SuccessHandler;
    private final OAuth2FailureHandler oauth2FailureHandler;
    private final HttpCookieOAuth2AuthorizationRequestRepository oauth2AuthorizationRequestRepository;
    private final List<String> allowedOrigins;
    private final boolean swaggerEnabled;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter,
                          RateLimitFilter rateLimitFilter,
                          TurnstileFilter turnstileFilter,
                          OAuth2SuccessHandler oauth2SuccessHandler,
                          OAuth2FailureHandler oauth2FailureHandler,
                          HttpCookieOAuth2AuthorizationRequestRepository oauth2AuthorizationRequestRepository,
                          @Value("${app.security.cors.allowed-origins:http://localhost:3000}") String origins,
                          @Value("${app.security.swagger-enabled:false}") boolean swaggerEnabled) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.turnstileFilter = turnstileFilter;
        this.oauth2SuccessHandler = oauth2SuccessHandler;
        this.oauth2FailureHandler = oauth2FailureHandler;
        this.oauth2AuthorizationRequestRepository = oauth2AuthorizationRequestRepository;
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        this.swaggerEnabled = swaggerEnabled;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfig()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(contentSecurityPolicy())))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/error").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                    // Prometheus scrape không gửi credential. Nếu để `authenticated()`
                    // thì scrape nhận 401 và dashboard Grafana trống im lặng.
                    // Lớp chặn ngoài là `allow 172.16.0.0/12; deny all` trong nginx
                    // (location = /api/v1/actuator/prometheus).
                    .requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll()
                    .requestMatchers(HttpMethod.GET, "/blog/posts", "/blog/posts/**", "/blog/feed.rss").permitAll()
                    // Ảnh đại diện do API phục vụ và tải bằng thẻ <img>: không có header Authorization.
                    .requestMatchers(HttpMethod.GET, "/users/*/avatar/*").permitAll()
                    .requestMatchers("/auth/register", "/auth/login", "/auth/forgot-password",
                                     "/auth/reset-password", "/auth/refresh", "/auth/logout",
                                     "/auth/email-verification/request", "/auth/verify-email").permitAll()
                    .requestMatchers("/login/**", "/oauth2/**").permitAll()
                    // Internal cron endpoints authenticate with their own shared token.
                    .requestMatchers(HttpMethod.POST, "/internal/**").permitAll();
                if (swaggerEnabled) {
                    auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                }
                auth.requestMatchers("/actuator/**").authenticated()
                    .anyRequest().authenticated();
            })
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(a -> a
                    .baseUri("/oauth2/authorization")
                    // State + PKCE code_verifier đi trong cookie mã hoá thay vì JSESSIONID:
                    // app STATELESS nên session cookie không đáng tin (iOS/mobile hay đánh rơi),
                    // mất nó là callback trả authorization_request_not_found một cách ngẫu nhiên.
                    .authorizationRequestRepository(oauth2AuthorizationRequestRepository))
                .successHandler(oauth2SuccessHandler)
                .failureHandler(oauth2FailureHandler)
            )
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                res.setStatus(401);
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"Authentication required\"}");
            }))
            // Rate limit trước (rẻ, chặn flood sớm), rồi mới tới Turnstile (gọi ra Cloudflare).
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(turnstileFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Swagger UI nhúng inline script/style; CSP `default-src 'self'` sẽ làm trang trắng.
     * Chỉ nới `script-src`/`style-src` khi swagger bật — prod tắt swagger nên giữ CSP chặt.
     */
    private String contentSecurityPolicy() {
        String base = "default-src 'self'; frame-ancestors 'self'; form-action 'self'";
        return swaggerEnabled
                ? base + "; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'"
                : base;
    }

    @Bean
    CorsConfigurationSource corsConfig() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
