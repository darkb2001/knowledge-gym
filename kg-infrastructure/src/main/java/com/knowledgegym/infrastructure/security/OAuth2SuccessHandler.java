package com.knowledgegym.infrastructure.security;

import com.knowledgegym.identity.application.HashUtils;
import com.knowledgegym.identity.domain.model.AuthProvider;
import com.knowledgegym.identity.domain.model.RefreshToken;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.RefreshTokenCachePort;
import com.knowledgegym.identity.domain.port.RefreshTokenRepository;
import com.knowledgegym.identity.domain.port.TokenService;
import com.knowledgegym.identity.domain.port.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Google OAuth2 success handler.
 * - email_verified=true bắt buộc
 * - Không silent-link LOCAL account (chống account takeover)
 * - Redirect FE allowlisted qua app.security.oauth2.success-redirect-uri
 */
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private static final int USER_AGENT_MAX_LEN = 500;

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCachePort cache;
    private final RefreshTokenCookie refreshCookie;
    private final ClientIpResolver clientIpResolver;
    private final SpringAuditLogger auditLogger;
    private final String successRedirectBase;

    public OAuth2SuccessHandler(UserRepository userRepository,
                                TokenService tokenService,
                                RefreshTokenRepository refreshTokenRepository,
                                RefreshTokenCachePort cache,
                                RefreshTokenCookie refreshCookie,
                                ClientIpResolver clientIpResolver,
                                SpringAuditLogger auditLogger,
                                @Value("${app.security.oauth2.success-redirect-uri:http://localhost:3000/auth/oauth2/success}")
                                String successRedirectBase) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.cache = cache;
        this.refreshCookie = refreshCookie;
        this.clientIpResolver = clientIpResolver;
        this.auditLogger = auditLogger;
        this.successRedirectBase = successRedirectBase;
        validateRedirectBase(successRedirectBase);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();

        Boolean emailVerified = oauthUser.getAttribute("email_verified");
        if (emailVerified == null || !emailVerified) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Email not verified by Google");
            return;
        }
        String email = oauthUser.getAttribute("email");
        String displayName = oauthUser.getAttribute("name");
        String googleSub = oauthUser.getName();
        if (email == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Google account has no email");
            return;
        }

        Optional<User> existing = userRepository.findByEmail(email);
        User user;
        if (existing.isEmpty()) {
            user = userRepository.save(User.createGoogleUser(email, displayName, googleSub));
        } else {
            user = existing.get();
            // Anti-takeover: không silent-link LOCAL → GOOGLE
            if (user.getAuthProvider() == AuthProvider.LOCAL
                    || (user.getOauthId() == null && user.getPasswordHash() != null)) {
                // Audit cả nhánh thất bại: đây là dấu hiệu account-takeover / user nhầm phương thức.
                auditLogger.oauthEmailConflict(email, clientIpResolver.resolve(request));
                response.sendError(HttpServletResponse.SC_CONFLICT,
                        "Account exists with password login. Sign in with email/password, then link Google from settings.");
                return;
            }
            if (user.getOauthId() != null && !user.getOauthId().equals(googleSub)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "OAuth identity mismatch");
                return;
            }
            if (user.getOauthId() == null) {
                user.setAuthProvider(AuthProvider.GOOGLE);
                user.setOauthId(googleSub);
                user = userRepository.save(user);
            }
        }

        if (user.isBlocked()) {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Account is blocked");
            return;
        }
        if (!user.isEmailVerified()) {
            user.verifyEmail();
            user = userRepository.save(user);
        }

        UUID familyId = UUID.randomUUID();
        String accessToken = tokenService.generateAccessToken(user.getId(), user.getRole().name());
        String rawRefresh = tokenService.generateRefreshToken(user.getId(), familyId);
        String refreshHash = HashUtils.sha256Hex(rawRefresh);

        RefreshToken audit = new RefreshToken(user.getId(), refreshHash, familyId);
        audit.setIpAddress(clientIpResolver.resolve(request));
        audit.setUserAgent(clientIpResolver.userAgent(request, USER_AGENT_MAX_LEN));
        refreshTokenRepository.save(audit);
        cache.store(refreshHash, user.getId(), familyId, RefreshToken.TTL);

        refreshCookie.write(response, rawRefresh);
        auditLogger.oauthSuccess(user.getId(), clientIpResolver.resolve(request));

        String redirect = successRedirectBase
                + "#accessToken=" + java.net.URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
                + "&userId=" + user.getId()
                + "&role=" + user.getRole().name()
                + "&provider=google";
        response.sendRedirect(redirect);
    }

    /** Chỉ cho phép http(s) absolute URL — chống open-redirect nếu config sai. */
    private static void validateRedirectBase(String base) {
        URI uri = URI.create(base);
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
            throw new IllegalStateException(
                    "app.security.oauth2.success-redirect-uri must be absolute http(s) URL, got: " + base);
        }
        if (uri.getHost() == null) {
            throw new IllegalStateException("oauth2 success-redirect-uri missing host: " + base);
        }
    }
}
