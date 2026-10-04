package com.knowledgegym.infrastructure.security;

import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockedOAuthAccountTest {
    /**
     * Tài khoản bị khoá: KHÔNG phát token/cookie, KHÔNG dựng phiên. Trước đây trả thẳng
     * `sendError(401)` nên trình duyệt hiện trang lỗi HTML của Tomcat; nay phải redirect về
     * trang lỗi của FE kèm `reason=account_blocked` để người dùng biết lý do.
     */
    @Test void blockedGoogleAccountCannotIssueTokensOrSetCookie() throws Exception {
        var users=mock(UserRepository.class); var tokens=mock(TokenService.class);
        var refresh=mock(RefreshTokenRepository.class); var cache=mock(RefreshTokenCachePort.class);
        var cookie=mock(RefreshTokenCookie.class); var ip=mock(ClientIpResolver.class); var audit=mock(SpringAuditLogger.class);
        var user=User.createGoogleUser("user@example.com","Learner","google-sub"); user.setBlocked(true);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var handler=new OAuth2SuccessHandler(users,tokens,refresh,cache,cookie,ip,audit,"https://example.test/auth/oauth2/success");
        var authorities=List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var principal=new DefaultOAuth2User(authorities,Map.of("sub","google-sub","email",user.getEmail(),"email_verified",true),"sub");
        var response=new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(),response,new OAuth2AuthenticationToken(principal,authorities,"google"));
        assertEquals("https://example.test/auth/oauth2/error?reason=account_blocked", response.getRedirectedUrl());
        verifyNoInteractions(tokens,refresh,cache,cookie);
        verify(audit).oauthFailure(any(), eq("account_blocked"));
    }
}
