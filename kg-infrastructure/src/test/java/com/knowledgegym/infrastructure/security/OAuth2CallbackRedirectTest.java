package com.knowledgegym.infrastructure.security;

import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OAuth2CallbackRedirectTest {

    /**
     * FE dựa vào `?oauth=google` đứng TRƯỚC fragment để biết redirect đến từ handler này và
     * dùng refresh cookie làm đường dự phòng khi `#accessToken=…` bị cắt trên đường đi.
     */
    @Test
    void successRedirectCarriesOauthMarkerBeforeFragment() throws Exception {
        var users = mock(UserRepository.class);
        var tokens = mock(TokenService.class);
        var refresh = mock(RefreshTokenRepository.class);
        var cache = mock(RefreshTokenCachePort.class);
        var cookie = mock(RefreshTokenCookie.class);
        var ip = mock(ClientIpResolver.class);
        var audit = mock(SpringAuditLogger.class);
        when(users.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokens.generateAccessToken(any(), any())).thenReturn("access-token");
        when(tokens.generateRefreshToken(any(), any())).thenReturn("refresh-token");
        var handler = new OAuth2SuccessHandler(users, tokens, refresh, cache, cookie, ip, audit,
                "https://app.example.test/auth/oauth2/success");
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var principal = new DefaultOAuth2User(authorities,
                Map.of("sub", "google-sub", "email", "new@example.com", "email_verified", true, "name", "New Learner"),
                "sub");
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                new OAuth2AuthenticationToken(principal, authorities, "google"));

        String url = response.getRedirectedUrl();
        assertNotNull(url, "phải redirect về FE, không phải sendError");
        assertTrue(url.startsWith("https://app.example.test/auth/oauth2/success?oauth=google#accessToken="), url);
        assertTrue(url.contains("&userId="), url);
        assertTrue(url.contains("&provider=google"), url);
        verify(refresh).save(any());
        verify(cookie).write(eq(response), eq("refresh-token"));
    }

    /** Log cũ chỉ ghi tên class ⇒ không tra được nguyên nhân thất bại. */
    @Test
    void failureReasonCarriesOAuthErrorCodeAndCause() {
        var oauth2 = new OAuth2AuthenticationException(
                new OAuth2Error("invalid_grant", "Malformed auth code.", null));
        String reason = OAuth2FailureHandler.describe(oauth2);
        assertTrue(reason.contains("invalid_grant"), reason);
        assertTrue(reason.contains("Malformed auth code."), reason);
        assertTrue(reason.length() <= 300, reason);
    }
}
