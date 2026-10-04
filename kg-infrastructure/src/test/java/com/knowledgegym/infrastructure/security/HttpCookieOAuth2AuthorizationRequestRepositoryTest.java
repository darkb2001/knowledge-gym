package com.knowledgegym.infrastructure.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * State OAuth2 phải sống sót qua một cookie mã hoá, không phụ thuộc HTTP session.
 * Bộ test này khoá các tính chất: round-trip đầy đủ (kể cả PKCE), chống giả mạo,
 * dùng một lần, và thuộc tính cookie.
 */
class HttpCookieOAuth2AuthorizationRequestRepositoryTest {

    private static final String SECRET = "test-access-secret-0123456789-abcdefghijklmnop";
    private static final String CONTEXT_PATH = "/api/v1";

    private final HttpCookieOAuth2AuthorizationRequestRepository repository =
            new HttpCookieOAuth2AuthorizationRequestRepository(SECRET);

    private static OAuth2AuthorizationRequest sampleRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("501436484096-abc.apps.googleusercontent.com")
                .redirectUri("https://api.darkb-tech.io.vn/api/v1/login/oauth2/code/google")
                .scopes(Set.of("email", "profile"))
                .state("state-token-xyz")
                .additionalParameters(Map.of(
                        "code_challenge", "v_0vd9VPKP5FavmGr6kR5v7z2bet5JAaGOGT4BXXJxk",
                        "code_challenge_method", "S256"))
                .attributes(Map.of("registration_id", "google"))
                .build();
    }

    private static MockHttpServletRequest emptyRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath(CONTEXT_PATH);
        return request;
    }

    /** Lấy giá trị cookie mới nhất trong header Set-Cookie (dạng "name=value"). */
    private static String setCookieValue(MockHttpServletResponse response) {
        List<String> headers = response.getHeaders("Set-Cookie");
        assertThat(headers).isNotEmpty();
        String header = headers.get(headers.size() - 1);
        return header.substring(0, header.indexOf(';'));
    }

    private static MockHttpServletRequest requestWithCookie(String cookiePair) {
        MockHttpServletRequest request = emptyRequest();
        int idx = cookiePair.indexOf('=');
        request.setCookies(new Cookie(cookiePair.substring(0, idx), cookiePair.substring(idx + 1)));
        return request;
    }

    @Test
    @DisplayName("save rồi load trả về đúng authorization request, giữ cả PKCE code_challenge")
    void roundTripKeepsStateAndPkce() {
        OAuth2AuthorizationRequest original = sampleRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(original, emptyRequest(), response);
        OAuth2AuthorizationRequest loaded =
                repository.loadAuthorizationRequest(requestWithCookie(setCookieValue(response)));

        assertThat(loaded).isNotNull();
        assertThat(loaded.getState()).isEqualTo(original.getState());
        assertThat(loaded.getClientId()).isEqualTo(original.getClientId());
        assertThat(loaded.getRedirectUri()).isEqualTo(original.getRedirectUri());
        assertThat(loaded.getAuthorizationUri()).isEqualTo(original.getAuthorizationUri());
        assertThat(loaded.getScopes()).isEqualTo(original.getScopes());
        assertThat(loaded.getAdditionalParameters()).isEqualTo(original.getAdditionalParameters());
        assertThat(loaded.getAttributes()).isEqualTo(original.getAttributes());
    }

    @Test
    @DisplayName("cookie bị sửa -> không load được (GCM tag chặn), không ném exception")
    void tamperedCookieIsRejected() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(sampleRequest(), emptyRequest(), response);
        String cookie = setCookieValue(response);

        String tampered = cookie.substring(0, cookie.length() - 4) + "AAAA";

        assertThat(repository.loadAuthorizationRequest(requestWithCookie(tampered))).isNull();
    }

    @Test
    @DisplayName("không có cookie -> null (user chưa bắt đầu flow)")
    void missingCookieReturnsNull() {
        assertThat(repository.loadAuthorizationRequest(emptyRequest())).isNull();
    }

    @Test
    @DisplayName("cookie do instance khác (khoá khác) tạo ra -> null, không lỗi 500")
    void cookieFromAnotherKeyIsRejected() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(sampleRequest(), emptyRequest(), response);
        String cookie = setCookieValue(response);

        HttpCookieOAuth2AuthorizationRequestRepository other =
                new HttpCookieOAuth2AuthorizationRequestRepository("another-secret-0123456789-abcdefghijkl");
        assertThat(other.loadAuthorizationRequest(requestWithCookie(cookie))).isNull();
    }

    @Test
    @DisplayName("remove vừa trả request vừa đặt cookie hết hạn (state dùng một lần)")
    void removeReturnsRequestAndExpiresCookie() {
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(sampleRequest(), emptyRequest(), saveResponse);

        MockHttpServletResponse callbackResponse = new MockHttpServletResponse();
        OAuth2AuthorizationRequest removed = repository.removeAuthorizationRequest(
                requestWithCookie(setCookieValue(saveResponse)), callbackResponse);

        assertThat(removed).isNotNull();
        assertThat(removed.getState()).isEqualTo("state-token-xyz");
        List<String> headers = callbackResponse.getHeaders("Set-Cookie");
        assertThat(headers).hasSize(1);
        assertThat(headers.get(0)).contains("Max-Age=0").contains("kg_oauth_req=");
    }

    @Test
    @DisplayName("thuộc tính cookie: HttpOnly, Secure, SameSite=Lax, Path=context path, Max-Age 300")
    void cookieAttributesAreHardened() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(sampleRequest(), emptyRequest(), response);

        String header = response.getHeaders("Set-Cookie").get(0);
        assertThat(header)
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .contains("Path=" + CONTEXT_PATH)
                .contains("Max-Age=" + HttpCookieOAuth2AuthorizationRequestRepository.MAX_AGE_SECONDS);
        // State không được lộ dạng plaintext ra client.
        assertThat(header).doesNotContain("state-token-xyz");
    }

    @Test
    @DisplayName("context path rỗng (dev) -> cookie Path=/")
    void rootContextPathFallsBackToSlash() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(sampleRequest(), new MockHttpServletRequest(), response);

        assertThat(response.getHeaders("Set-Cookie").get(0)).contains("Path=/;");
    }
}
