package com.knowledgegym.presentation.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata + bearer auth scheme để "Authorize" trong Swagger UI gửi được JWT.
 * Chỉ tạo khi `app.security.swagger-enabled=true` (dev bật trong `application.yml`, prod tắt).
 * Default = false để khớp `SecurityConfig` — không permitAll `/v3/api-docs/**` khi chưa bật.
 */
@Configuration
@ConditionalOnProperty(name = "app.security.swagger-enabled", havingValue = "true")
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI knowledgeGymOpenApi(@Value("${app.version:0.4.0}") String version) {
        return new OpenAPI()
                .info(new Info()
                        .title("Knowledge Gym API")
                        .version(version)
                        .description("""
                                REST API cho Knowledge Gym — ngân hàng câu hỏi phỏng vấn Java.

                                Base path: `/api/v1`.

                                Xác thực: gọi `POST /api/v1/auth/login` → copy `accessToken`
                                → bấm **Authorize** → dán token.
                                """)
                        .license(new io.swagger.v3.oas.models.info.License().name("Private")))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
