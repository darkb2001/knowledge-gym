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
import java.util.Optional;
import java.util.UUID;

/**
 * Google OAuth2 success handler — cập nhật user identity theo email_verified=true,
 * issue access JWT + refresh token pair (giống password login),
 * set refresh cookie (httpOnly) + redirect FE với access token trong URL fragment.
 *
 * Fragment pattern: `/auth/oauth2/success#accessToken=...&userId=...`
 * Access token KHÔNG vào cookie (FE đọc qua window.location.hash, XSS risk accepted cho SPA);
 * refresh token nằm trong httpOnly cookie — refresh được mà XSS không đọc được.
 */
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCachePort cache;
    private final RefreshTokenCookie refreshCookie;

    public OAuth2SuccessHandler(UserRepository userRepository,
                                TokenService tokenService,
                                RefreshTokenRepository refreshTokenRepository,
                                RefreshTokenCachePort cache,
                                RefreshTokenCookie refreshCookie) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.cache = cache;
        this.refreshCookie = refreshCookie;
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
        User user = existing.orElseGet(() -> {
            User u = new User(email, null, displayName != null ? displayName : email);
            u.setAuthProvider(AuthProvider.GOOGLE);
            u.setOauthId(googleSub);
            return userRepository.save(u);
        });

        // Cập nhật oauth_id nếu lần đầu link (bảo mật: chỉ link khi email trùng)
        if (user.getOauthId() == null) {
            user.setAuthProvider(AuthProvider.GOOGLE);
            user.setOauthId(googleSub);
            userRepository.save(user);
        }

        // Issue token pair — giống password login để OAuth user refresh được (blocker #3)
        String familyId = UUID.randomUUID().toString();
        String accessToken = tokenService.generateAccessToken(user.getId(), user.getRole().name());
        String rawRefresh = tokenService.generateRefreshToken(user.getId(), UUID.fromString(familyId));
        String refreshHash = HashUtils.sha256Hex(rawRefresh);

        RefreshToken audit = new RefreshToken(user.getId(), refreshHash, UUID.fromString(familyId));
        audit.setIpAddress(clientIp(request));
        audit.setUserAgent(request.getHeader("User-Agent"));
        refreshTokenRepository.save(audit);
        cache.store(refreshHash, user.getId(), audit.getFamilyId(), RefreshToken.TTL);

        refreshCookie.write(response, rawRefresh);

        // Fragment redirect — access token cho FE ngay, không cần gọi /auth/refresh
        String redirect = "/auth/oauth2/success#accessToken=" + java.net.URLEncoder.encode(accessToken, java.nio.charset.StandardCharsets.UTF_8)
                + "&userId=" + user.getId()
                + "&role=" + user.getRole().name()
                + "&provider=google";
        response.sendRedirect(redirect);
    }

    private String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) return forwarded.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}