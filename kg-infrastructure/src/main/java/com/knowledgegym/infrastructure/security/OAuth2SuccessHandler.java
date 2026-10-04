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
    private final String errorRedirectBase;

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
        this.errorRedirectBase = deriveErrorRedirect(successRedirectBase);
        validateRedirectBase(successRedirectBase);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();

        Boolean emailVerified = oauthUser.getAttribute("email_verified");
        if (emailVerified == null || !emailVerified) {
            auditLogger.oauthFailure(clientIpResolver.resolve(request), "google_email_unverified");
            redirectToError(response, "google_email_unverified");
            return;
        }
        String email = oauthUser.getAttribute("email");
        String displayName = oauthUser.getAttribute("name");
        String googleSub = oauthUser.getName();
        if (email == null) {
            auditLogger.oauthFailure(clientIpResolver.resolve(request), "google_no_email");
            redirectToError(response, "google_no_email");
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
                redirectToError(response, "password_account");
                return;
            }
            if (user.getOauthId() != null && !user.getOauthId().equals(googleSub)) {
                auditLogger.oauthFailure(clientIpResolver.resolve(request), "oauth_identity_mismatch");
                redirectToError(response, "oauth_identity_mismatch");
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
            auditLogger.oauthFailure(clientIpResolver.resolve(request), "account_blocked");
            redirectToError(response, "account_blocked");
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

        // `?oauth=google` cho FE biết redirect này đến từ handler này: khi phần fragment
        // (#accessToken=…) bị cắt trên đường đi (proxy/edge), FE vẫn có đường dự phòng là
        // đổi refresh cookie HttpOnly ở trên lấy access token mới.
        String separator = successRedirectBase.contains("?") ? "&" : "?";
        String redirect = successRedirectBase + separator + "oauth=google"
                + "#accessToken=" + java.net.URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
                + "&userId=" + user.getId()
                + "&role=" + user.getRole().name()
                + "&provider=google";
        response.sendRedirect(redirect);
    }

    /**
     * Lỗi nghiệp vụ giữa luồng OAuth (email Google chưa xác minh, email đã có tài khoản mật khẩu,
     * tài khoản bị khoá…) trước đây trả `sendError` ⇒ trình duyệt nhận trang lỗi HTML của Tomcat:
     * người dùng chỉ thấy "HTTP Status 409" và không biết phải làm gì. Redirect về trang lỗi của
     * FE kèm `?reason=<mã>` để FE hiển thị đúng hướng dẫn cho từng trường hợp.
     */
    private void redirectToError(HttpServletResponse response, String reason) throws IOException {
        String separator = errorRedirectBase.contains("?") ? "&" : "?";
        response.sendRedirect(errorRedirectBase + separator + "reason="
                + java.net.URLEncoder.encode(reason, StandardCharsets.UTF_8));
    }

    /**
     * `/auth/oauth2/success` → `/auth/oauth2/error`, giữ nguyên origin (và query nếu có) nên không
     * cần thêm biến môi trường: đích redirect vẫn nằm trong allowlist đã cấu hình cho đăng nhập thành công.
     */
    static String deriveErrorRedirect(String successRedirectBase) {
        int query = successRedirectBase.indexOf('?');
        String path = query < 0 ? successRedirectBase : successRedirectBase.substring(0, query);
        String tail = query < 0 ? "" : successRedirectBase.substring(query);
        String derived = path.endsWith("/success")
                ? path.substring(0, path.length() - "/success".length()) + "/error"
                : path.replaceAll("/+$", "") + "/error";
        return derived + tail;
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
