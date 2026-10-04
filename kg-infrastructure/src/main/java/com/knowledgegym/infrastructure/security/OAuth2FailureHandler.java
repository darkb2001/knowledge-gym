package com.knowledgegym.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Redirects OAuth failures to the configured frontend error route instead of Spring's /login?error. */
@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    /** audit_logs.details là text tự do, cắt bớt để không phình bản ghi. */
    private static final int MAX_REASON_LEN = 300;

    private final String failureRedirectBase;
    private final SpringAuditLogger auditLogger;
    private final ClientIpResolver clientIpResolver;

    public OAuth2FailureHandler(
            @Value("${app.security.oauth2.failure-redirect-uri:http://localhost:3000/auth/oauth2/error}")
            String failureRedirectBase,
            SpringAuditLogger auditLogger,
            ClientIpResolver clientIpResolver) {
        this.failureRedirectBase = failureRedirectBase;
        this.auditLogger = auditLogger;
        this.clientIpResolver = clientIpResolver;
        validateRedirectBase(failureRedirectBase);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        auditLogger.oauthFailure(clientIpResolver.resolve(request), describe(exception));
        String separator = failureRedirectBase.contains("?") ? "&" : "?";
        String redirect = failureRedirectBase + separator + "reason="
                + URLEncoder.encode(reasonCode(exception), StandardCharsets.UTF_8);
        response.sendRedirect(redirect);
    }

    /**
     * Mã lỗi gửi cho FE. Trước đây luôn là "oauth_failed" nên FE không phân biệt được phiên đăng
     * nhập Google đã hết hạn (`authorization_request_not_found` — người dùng chỉ cần bấm lại) với
     * người dùng tự huỷ (`access_denied`). Chỉ gửi mã, KHÔNG gửi description (có thể chứa chi tiết
     * nội bộ); chi tiết đầy đủ vẫn nằm trong audit log.
     */
    static String reasonCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2) {
            String code = oauth2.getError().getErrorCode();
            if (code != null && !code.isBlank()) {
                return code;
            }
        }
        return "oauth_failed";
    }

    /**
     * Chỉ ghi tên class là vô dụng khi tra log ("OAuth2AuthenticationException" không nói
     * được state sai, code đã dùng hay Google từ chối). Ghi mã lỗi OAuth2 + mô tả + cause.
     */
    static String describe(AuthenticationException exception) {
        StringBuilder reason = new StringBuilder(exception.getClass().getSimpleName());
        if (exception instanceof OAuth2AuthenticationException oauth2) {
            reason.append('[').append(oauth2.getError().getErrorCode());
            String description = oauth2.getError().getDescription();
            if (description != null && !description.isBlank()) {
                reason.append(": ").append(description);
            }
            reason.append(']');
        }
        Throwable cause = exception.getCause();
        if (cause != null) {
            reason.append(" <- ").append(cause.getClass().getSimpleName());
            if (cause.getMessage() != null) {
                reason.append(": ").append(cause.getMessage());
            }
        }
        String text = reason.toString().replaceAll("\\s+", " ").trim();
        return text.length() > MAX_REASON_LEN ? text.substring(0, MAX_REASON_LEN) : text;
    }

    private static void validateRedirectBase(String base) {
        URI uri = URI.create(base);
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equals("http") && !scheme.equals("https")) || uri.getHost() == null) {
            throw new IllegalStateException(
                    "app.security.oauth2.failure-redirect-uri must be an absolute http(s) URL, got: " + base);
        }
    }
}
