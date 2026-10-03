package com.knowledgegym.presentation;

import com.knowledgegym.presentation.rest.auth.RegisterRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestValidationTest {
    @Test
    void matchingPasswordsAreAccepted() {
        assertValid(new RegisterRequest("user@example.com", "password123", "password123", "User", "123456"), true);
    }

    @Test
    void missingBlankOrMismatchedConfirmationIsRejected() {
        assertValid(new RegisterRequest("user@example.com", "password123", null, "User", "123456"), false);
        assertValid(new RegisterRequest("user@example.com", "password123", "", "User", "123456"), false);
        assertValid(new RegisterRequest("user@example.com", "password123", "different123", "User", "123456"), false);
        assertValid(new RegisterRequest("user@example.com", "password123", "password123 ", "User", "123456"), false);
    }

    @Test
    void missingOrMalformedVerificationCodeIsRejected() {
        for (String code : new String[] {null, "", "12345", "1234567", "ABCDEF"}) {
            assertValid(new RegisterRequest("user@example.com", "password123", "password123", "User", code), false);
        }
    }

    @Test
    void requestLoggingDoesNotExposePasswords() {
        assertThat(new RegisterRequest("user@example.com", "password123", "password123", "User", "123456").toString())
                .doesNotContain("password123", "123456");
    }

    private void assertValid(RegisterRequest request, boolean expected) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request).isEmpty()).isEqualTo(expected);
        }
    }
}
