package com.knowledgegym.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail-fast nếu prod profile vẫn dùng JWT secret mặc định (dev placeholder).
 * Tránh boot với secret yếu đã commit vào source.
 */
@Component
public class JwtSecretValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretValidator.class);
    private static final String DEV_MARKER = "dev-access-secret";

    private final Environment env;
    private final String accessSecret;
    private final String refreshSecret;

    public JwtSecretValidator(Environment env,
                               @Value("${app.security.jwt.access-secret}") String accessSecret,
                               @Value("${app.security.jwt.refresh-secret}") String refreshSecret) {
        this.env = env;
        this.accessSecret = accessSecret;
        this.refreshSecret = refreshSecret;
    }

    @PostConstruct
    void validate() {
        boolean prod = ArraysContains(env.getActiveProfiles(), "prod");
        boolean weak = accessSecret.contains(DEV_MARKER)
                || refreshSecret.contains("dev-refresh-secret")
                || accessSecret.length() < 32
                || refreshSecret.length() < 32;
        if (prod && weak) {
            throw new IllegalStateException(
                    "FATAL: Weak/default JWT secrets in prod profile. "
                            + "Set JWT_ACCESS_SECRET and JWT_REFRESH_SECRET (min 32 chars).");
        }
        if (weak) {
            log.warn("JWT secrets look like defaults — OK for local/dev, NEVER use in production");
        }
    }

    private static boolean ArraysContains(String[] arr, String value) {
        for (String s : arr) {
            if (value.equals(s)) return true;
        }
        return false;
    }
}