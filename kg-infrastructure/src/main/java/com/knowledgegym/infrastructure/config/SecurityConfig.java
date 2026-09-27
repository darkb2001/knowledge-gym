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
    private final OAuth2SuccessHandler oauth2SuccessHandler;
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter,
                          RateLimitFilter rateLimitFilter,
                          OAuth2SuccessHandler oauth2SuccessHandler,
                          @Value("${app.security.cors.allowed-origins:http://localhost:3000}") String origins) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.oauth2SuccessHandler = oauth2SuccessHandler;
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfig()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(
                    "default-src 'self'; frame-ancestors 'self'; form-action 'self'")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/auth/register", "/auth/login", "/auth/forgot-password",
                                 "/auth/reset-password", "/auth/refresh", "/auth/logout").permitAll()
                .requestMatchers("/login/**", "/oauth2/**").permitAll()
                .requestMatchers("/actuator/**").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(a -> a.baseUri("/oauth2/authorization"))
                .successHandler(oauth2SuccessHandler)
            )
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                res.setStatus(401);
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"Authentication required\"}");
            }))
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfig() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}