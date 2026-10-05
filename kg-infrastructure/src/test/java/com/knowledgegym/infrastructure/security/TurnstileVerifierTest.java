package com.knowledgegym.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bản chất: bật/tắt enforcement, từ chối token thiếu/sai, và chính sách khi Cloudflare không gọi được.
 */
class TurnstileVerifierTest {

    private static final String SECRET = "test-secret";

    private static class Fake extends TurnstileVerifier {
        private final Map<String, Object> response;
        private final RuntimeException failure;

        Fake(boolean enabled, String secret, boolean failOpen, Map<String, Object> response) {
            this(enabled, secret, failOpen, response, null);
        }

        Fake(boolean enabled, String secret, boolean failOpen, Map<String, Object> response, RuntimeException failure) {
            super(enabled, secret, "http://localhost/siteverify", 500, failOpen);
            this.response = response;
            this.failure = failure;
        }

        @Override
        protected Map<String, Object> postVerify(String token, String remoteIp) {
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }

    @Test void disabledAllowsEverythingWithoutToken() {
        var verifier = new Fake(false, SECRET, true, Map.of("success", false));
        assertFalse(verifier.isEnabled());
        assertTrue(verifier.verify(null, "1.2.3.4"));
    }

    @Test void enabledWithoutSecretStaysDisabled() {
        var verifier = new Fake(true, "  ", true, Map.of("success", true));
        assertFalse(verifier.isEnabled());
        assertTrue(verifier.verify(null, null));
    }

    @Test void missingTokenIsRejected() {
        var verifier = new Fake(true, SECRET, true, Map.of("success", true));
        assertFalse(verifier.verify(null, "1.2.3.4"));
        assertFalse(verifier.verify("   ", "1.2.3.4"));
    }

    @Test void cloudflareSuccessAllows() {
        var verifier = new Fake(true, SECRET, true, Map.of("success", true));
        assertTrue(verifier.verify("token", "1.2.3.4"));
    }

    @Test void cloudflareRejectionBlocks() {
        var verifier = new Fake(true, SECRET, true, Map.of("success", false, "error-codes", java.util.List.of("invalid-input-response")));
        assertFalse(verifier.verify("token", "1.2.3.4"));
    }

    @Test void networkFailureFailsOpenByDefault() {
        var verifier = new Fake(true, SECRET, true, null, new IllegalStateException("timeout"));
        assertTrue(verifier.verify("token", "1.2.3.4"));
    }

    @Test void networkFailureCanFailClosed() {
        var verifier = new Fake(true, SECRET, false, null, new IllegalStateException("timeout"));
        assertFalse(verifier.verify("token", "1.2.3.4"));
    }

    @Test void errorCodesAreReadFromResponse() {
        assertEquals(java.util.List.of("timeout-or-duplicate"),
                TurnstileVerifier.errorCodes(Map.of("success", false, "error-codes", java.util.List.of("timeout-or-duplicate"))));
        assertEquals(java.util.List.of(), TurnstileVerifier.errorCodes(Map.of("success", true)));
    }
}
