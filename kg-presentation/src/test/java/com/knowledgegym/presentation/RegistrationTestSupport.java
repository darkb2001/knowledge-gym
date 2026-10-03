package com.knowledgegym.presentation;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.concurrent.atomic.AtomicInteger;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class RegistrationTestSupport {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    /** Per-request forwarded IP: `ClientIpResolver` trusts XFF in the `test` profile, so
     *  the per-IP buckets of `/auth/register` (10/hour) and `/auth/*` (100/min) never
     *  spill between neighbouring tests. */
    static String nextIp() {
        return "198.51.100." + (1 + SEQUENCE.incrementAndGet() % 250);
    }

    static String code(MockMvc mvc, String email) throws Exception {
        mvc.perform(post("/auth/email-verification/request")
                        .header("X-Forwarded-For", nextIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
        String code = TestEmailServiceConfig.VERIFICATION_CODES.get(email.toLowerCase());
        if (code == null) throw new AssertionError("Verification email was not captured");
        return code;
    }
}
