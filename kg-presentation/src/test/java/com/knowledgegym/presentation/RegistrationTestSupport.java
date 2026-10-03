package com.knowledgegym.presentation;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.concurrent.atomic.AtomicInteger;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class RegistrationTestSupport {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    static String code(MockMvc mvc, String email) throws Exception {
        mvc.perform(post("/auth/email-verification/request")
                        .header("X-Forwarded-For", "198.51.100." + (1 + SEQUENCE.incrementAndGet() % 250))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
        String code = TestEmailServiceConfig.VERIFICATION_CODES.get(email.toLowerCase());
        if (code == null) throw new AssertionError("Verification email was not captured");
        return code;
    }
}
