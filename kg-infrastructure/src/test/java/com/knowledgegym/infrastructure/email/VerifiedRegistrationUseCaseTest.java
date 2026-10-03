package com.knowledgegym.infrastructure.email;

import com.knowledgegym.identity.application.*;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.*;
import com.knowledgegym.shared.domain.model.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class VerifiedRegistrationUseCaseTest {
    private final RegisterUseCase registration = mock(RegisterUseCase.class);
    private final UserRepository users = mock(UserRepository.class);
    private final EmailVerificationPort challenges = mock(EmailVerificationPort.class);
    private final EmailService mail = mock(EmailService.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final RefreshTokenCachePort cache = mock(RefreshTokenCachePort.class);
    private final VerifiedRegistrationUseCase service =
            new VerifiedRegistrationUseCase(registration, users, challenges, mail, passwords, tokens, cache);

    @Test
    void invalidCodeNeverCreatesAccountOrSession() {
        assertThatThrownBy(() -> service.register("user@example.com", "password123", "User", "123456"))
                .isInstanceOf(AuthException.class);
        verifyNoInteractions(registration);
        verify(users, never()).save(any());
    }

    @Test
    void verifiedRegistrationPreservesDefaultUserRole() {
        User user = new User("user@example.com", "hash", "User");
        when(challenges.consume("user@example.com", HashUtils.sha256Hex("123456"))).thenReturn(true);
        when(registration.execute("user@example.com", "password123", "User")).thenReturn(user);
        when(users.save(user)).thenReturn(user);
        User saved = service.register(" USER@example.com ", "password123", "User", "123456");
        assertThat(saved.isEmailVerified()).isTrue();
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void requestStoresOnlyHashAndSendsDedicatedVerificationMail() {
        when(challenges.issue(eq("user@example.com"), anyString())).thenReturn(true);
        service.requestCode(" USER@example.com ");
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mail).sendEmailVerificationCode(eq("user@example.com"), code.capture());
        assertThat(code.getValue()).matches("[0-9]{6}");
        verify(challenges).issue("user@example.com", HashUtils.sha256Hex(code.getValue()));
        verify(mail, never()).sendPasswordResetCode(anyString(), anyString());
    }

    @Test
    void legacyVerificationReplacesPotentialAttackerPasswordAndRevokesSessions() {
        User user = new User("user@example.com", "attacker-chosen-hash", "User");
        var family = java.util.UUID.randomUUID();
        when(challenges.consume("user@example.com", HashUtils.sha256Hex("123456"))).thenReturn(true);
        when(users.findByEmail("user@example.com")).thenReturn(java.util.Optional.of(user));
        when(passwords.hash("newPassword123")).thenReturn("owner-chosen-hash");
        when(tokens.findActiveFamilyIdsByUserId(user.getId())).thenReturn(java.util.Set.of(family));
        service.verifyExisting("user@example.com", "123456", "newPassword123");
        assertThat(user.getPasswordHash()).isEqualTo("owner-chosen-hash");
        assertThat(user.isEmailVerified()).isTrue();
        verify(cache).revokeFamily(eq(family), any());
        verify(tokens).revokeAllByUserId(user.getId());
        verifyNoInteractions(registration);
    }

    @Test
    void cooldownDoesNotSendAnotherMail() {
        when(challenges.issue(anyString(), anyString())).thenReturn(false);
        service.requestCode("user@example.com");
        verifyNoInteractions(mail);
    }

    @Test
    void failedSmtpInvalidatesChallengeAndPropagatesFailure() {
        when(challenges.issue(anyString(), anyString())).thenReturn(true);
        doThrow(new IllegalStateException("SMTP unavailable")).when(mail)
                .sendEmailVerificationCode(anyString(), anyString());
        assertThatThrownBy(() -> service.requestCode("user@example.com"))
                .isInstanceOf(IllegalStateException.class);
        verify(challenges).invalidate(eq("user@example.com"), anyString());
    }
}
