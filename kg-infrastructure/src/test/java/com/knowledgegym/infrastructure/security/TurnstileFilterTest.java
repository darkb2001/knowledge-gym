package com.knowledgegym.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TurnstileFilterTest {

    private final TurnstileVerifier verifier = mock(TurnstileVerifier.class);
    private final ClientIpResolver ipResolver = mock(ClientIpResolver.class);
    private final TurnstileFilter filter = new TurnstileFilter(verifier, ipResolver);

    private static MockHttpServletRequest post(String path) {
        var request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        return request;
    }

    @Test void disabledFilterLetsEverythingThrough() throws Exception {
        when(verifier.isEnabled()).thenReturn(false);
        var response = new MockHttpServletResponse();
        filter.doFilter(post("/auth/register"), response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertEquals(200, response.getStatus());
        verifyNoInteractions(ipResolver);
    }

    @Test void protectedPathWithoutTokenIs403AndNeverReachesController() throws Exception {
        when(verifier.isEnabled()).thenReturn(true);
        when(verifier.verify(any(), any())).thenReturn(false);
        when(ipResolver.resolve(any())).thenReturn("1.2.3.4");
        var response = new MockHttpServletResponse();
        filter.doFilter(post("/auth/register"), response, (req, res) -> fail("controller không được gọi"));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("turnstile_failed"));
    }

    @Test void protectedPathWithValidTokenContinues() throws Exception {
        when(verifier.isEnabled()).thenReturn(true);
        when(verifier.verify("good-token", "1.2.3.4")).thenReturn(true);
        when(ipResolver.resolve(any())).thenReturn("1.2.3.4");
        var request = post("/auth/forgot-password");
        request.addHeader(TurnstileFilter.HEADER, "good-token");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(204));
        assertEquals(204, response.getStatus());
    }

    @Test void loginIsNotProtectedEvenWhenEnabled() throws Exception {
        when(verifier.isEnabled()).thenReturn(true);
        var response = new MockHttpServletResponse();
        filter.doFilter(post("/auth/login"), response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertEquals(200, response.getStatus());
        verify(verifier, never()).verify(any(), any());
    }

    @Test void getOnProtectedPathIsNotFiltered() throws Exception {
        when(verifier.isEnabled()).thenReturn(true);
        var response = new MockHttpServletResponse();
        var request = new MockHttpServletRequest("GET", "/auth/register");
        request.setServletPath("/auth/register");
        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertEquals(200, response.getStatus());
        verify(verifier, never()).verify(any(), any());
    }

    @Test void contextPathIsStrippedBeforeMatching() throws Exception {
        when(verifier.isEnabled()).thenReturn(true);
        when(verifier.verify(any(), any())).thenReturn(false);
        when(ipResolver.resolve(any())).thenReturn("1.2.3.4");
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.setContextPath("/api/v1");
        request.setServletPath("/auth/register");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("phải bị chặn"));
        assertEquals(403, response.getStatus());
    }
}
