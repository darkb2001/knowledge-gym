package com.knowledgegym.infrastructure.security;

import com.knowledgegym.identity.domain.model.AuthProvider;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Lỗi giữa luồng OAuth phải đưa người dùng về trang lỗi của FE kèm mã lý do cụ thể, thay vì trang
 * lỗi HTML của Tomcat ("HTTP Status 409/401") mà họ không biết phải làm gì tiếp.
 */
class OAuthErrorRedirectTest {

    private static OAuth2AuthenticationToken token(String email, Boolean verified, String sub, String name) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var attrs = new java.util.HashMap<String, Object>();
        attrs.put("sub", sub);
        attrs.put("name", name);
        if (email != null) attrs.put("email", email);
        if (verified != null) attrs.put("email_verified", verified);
        return new OAuth2AuthenticationToken(
                new DefaultOAuth2User(authorities, attrs, "sub"), authorities, "google");
    }

    private record Harness(OAuth2SuccessHandler handler, UserRepository users,
                           SpringAuditLogger audit) {}

    private static Harness harness(String base) {
        var users = mock(UserRepository.class);
        var audit = mock(SpringAuditLogger.class);
        var handler = new OAuth2SuccessHandler(users, mock(TokenService.class),
                mock(RefreshTokenRepository.class), mock(RefreshTokenCachePort.class),
                mock(RefreshTokenCookie.class), mock(ClientIpResolver.class), audit, base);
        return new Harness(handler, users, audit);
    }

    @Test void errorRedirectDerivesFromSuccessRedirect() {
        assertEquals("https://app.example.test/auth/oauth2/error",
                OAuth2SuccessHandler.deriveErrorRedirect("https://app.example.test/auth/oauth2/success"));
        assertEquals("http://localhost:3000/auth/oauth2/error",
                OAuth2SuccessHandler.deriveErrorRedirect("http://localhost:3000/auth/oauth2/success"));
        // có query thì phải giữ nguyên query cũ
        assertEquals("https://app.example.test/auth/oauth2/error?tenant=kg",
                OAuth2SuccessHandler.deriveErrorRedirect("https://app.example.test/auth/oauth2/success?tenant=kg"));
    }

    @Test void localPasswordAccountIsNotSilentlyLinked() throws Exception {
        var h = harness("https://app.example.test/auth/oauth2/success");
        var user = User.createGoogleUser("user@example.com", "Learner", "google-sub");
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setPasswordHash("$2a$10$hash");
        when(h.users().findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var response = new MockHttpServletResponse();

        h.handler().onAuthenticationSuccess(new MockHttpServletRequest(), response,
                token(user.getEmail(), true, "google-sub", "Learner"));

        assertEquals("https://app.example.test/auth/oauth2/error?reason=password_account",
                response.getRedirectedUrl());
        verify(h.audit()).oauthEmailConflict(eq(user.getEmail()), any());
    }

    @Test void identityMismatchRedirectsWithReason() throws Exception {
        var h = harness("https://app.example.test/auth/oauth2/success");
        var user = User.createGoogleUser("user@example.com", "Learner", "other-sub");
        when(h.users().findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var response = new MockHttpServletResponse();

        h.handler().onAuthenticationSuccess(new MockHttpServletRequest(), response,
                token(user.getEmail(), true, "google-sub", "Learner"));

        assertEquals("https://app.example.test/auth/oauth2/error?reason=oauth_identity_mismatch",
                response.getRedirectedUrl());
    }

    @Test void unverifiedGoogleEmailRedirectsWithReason() throws Exception {
        var h = harness("https://app.example.test/auth/oauth2/success");
        var response = new MockHttpServletResponse();

        h.handler().onAuthenticationSuccess(new MockHttpServletRequest(), response,
                token("user@example.com", false, "google-sub", "Learner"));

        assertEquals("https://app.example.test/auth/oauth2/error?reason=google_email_unverified",
                response.getRedirectedUrl());
        verify(h.users(), never()).save(any());
    }

    @Test void failureHandlerPassesRealErrorCodeToFrontend() throws Exception {
        var handler = new OAuth2FailureHandler("https://app.example.test/auth/oauth2/error",
                mock(SpringAuditLogger.class), mock(ClientIpResolver.class));
        var response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new OAuth2AuthenticationException(new OAuth2Error("authorization_request_not_found",
                        "state/PKCE mất", null)));

        assertEquals("https://app.example.test/auth/oauth2/error?reason=authorization_request_not_found",
                response.getRedirectedUrl());
    }

    @Test void failureHandlerFallsBackToGenericCode() throws Exception {
        assertEquals("oauth_failed",
                OAuth2FailureHandler.reasonCode(new BadCredentialsException("nope")));
        assertEquals("access_denied", OAuth2FailureHandler.reasonCode(
                new OAuth2AuthenticationException(new OAuth2Error("access_denied", "user cancelled", null))));
    }
}
