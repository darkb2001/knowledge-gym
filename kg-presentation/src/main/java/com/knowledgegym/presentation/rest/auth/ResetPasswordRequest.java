package com.knowledgegym.presentation.rest.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 6, message = "Mã phải đúng 6 chữ số") String code,
        @NotBlank @Size(min = 8, max = 72) String newPassword) {}