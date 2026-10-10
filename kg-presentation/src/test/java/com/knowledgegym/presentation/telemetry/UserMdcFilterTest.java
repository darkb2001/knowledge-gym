package com.knowledgegym.presentation.telemetry;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class UserMdcFilterTest {
    private final UserMdcFilter filter = new UserMdcFilter();
    private final UUID userId = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @AfterEach void clean() { SecurityContextHolder.clearContext(); MDC.clear(); }

    /** An empty authority list is how Spring builds an unauthenticated token. */
    private void authenticate(Object principal, boolean authenticated) {
        var token = new UsernamePasswordAuthenticationToken(principal, null, authenticated
                ? List.of(new SimpleGrantedAuthority("ROLE_USER")) : List.of());
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private void filter(java.util.function.Consumer<String> assertions) throws Exception {
        var request = new MockHttpServletRequest("GET", "/notes");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> assertions.accept(MDC.get(UserMdcFilter.USER_ID)));
    }

    @Test void bindsAccountIdFromSecurityContextForTheWholeRequest() throws Exception {
        authenticate(userId, true);
        filter(actor -> assertEquals(userId.toString(), actor));
    }

    @Test void publishesNothingWithoutARealPrincipal() throws Exception {
        filter(actor -> assertNull(actor));                                    // no context at all
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        filter(actor -> assertNull(actor));                                    // anonymous is authenticated() == true
        authenticate("alice@example.test", true);                              // non-UUID principal, e.g. OAuth2 string
        filter(actor -> assertNull(actor));
    }

    @Test void neverOverwritesAnActorAlreadyBoundToTheRequest() throws Exception {
        authenticate(userId, true);
        MDC.put(UserMdcFilter.USER_ID, "outer-actor");
        filter(actor -> assertEquals("outer-actor", actor));
    }
}
