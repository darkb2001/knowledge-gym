package com.knowledgegym.presentation.rest.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(min = 8, max = 72) String confirmPassword,
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "[0-9]{6}") String verificationCode) {

    @AssertTrue(message = "Passwords do not match")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }

    // Never expose passwords in diagnostic logging of the request object.
    @Override
    public String toString() {
        return "RegisterRequest[credentials=REDACTED]";
    }
}