package com.knowledgegym.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Redirects OAuth failures to the configured frontend error route instead of Spring's /login?error. */
@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

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
        auditLogger.oauthFailure(clientIpResolver.resolve(request), exception.getClass().getSimpleName());
        String separator = failureRedirectBase.contains("?") ? "&" : "?";
        String redirect = failureRedirectBase + separator + "reason="
                + URLEncoder.encode("oauth_failed", StandardCharsets.UTF_8);
        response.sendRedirect(redirect);
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
