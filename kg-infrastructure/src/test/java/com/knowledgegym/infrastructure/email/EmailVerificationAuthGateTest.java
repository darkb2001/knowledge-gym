package com.knowledgegym.infrastructure.email;

import com.knowledgegym.identity.application.*;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailVerificationAuthGateTest {
    @Test
    void unverifiedPasswordLoginCannotIssueTokens() {
        var users = mock(UserRepository.class);
        var passwords = mock(PasswordHasher.class);
        var tokens = mock(TokenService.class);
        var refresh = mock(RefreshTokenRepository.class);
        var cache = mock(RefreshTokenCachePort.class);
        User user = new User("user@example.com", "hash", "User");
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("password123", "hash")).thenReturn(true);
        assertThatThrownBy(() -> new LoginUseCase(users, passwords, tokens, refresh, cache)
                .execute("user@example.com", "password123", "127.0.0.1", "test"))
                .isInstanceOf(AuthException.class).hasMessage("Email verification required");
        verifyNoInteractions(tokens, refresh, cache);
    }

    @Test
    void oldRefreshTokenCannotBypassVerificationGate() {
        var users = mock(UserRepository.class);
        var tokens = mock(TokenService.class);
        var refresh = mock(RefreshTokenRepository.class);
        var cache = mock(RefreshTokenCachePort.class);
        var revoker = mock(RefreshFamilyRevoker.class);
        User user = new User("user@example.com", "hash", "User");
        UUID family = UUID.randomUUID();
        when(tokens.verifyRefreshToken("old-refresh"))
                .thenReturn(new TokenService.RefreshTokenClaims(user.getId(), family, java.time.Instant.now()));
        when(cache.find(HashUtils.sha256Hex("old-refresh")))
                .thenReturn(Optional.of(new RefreshTokenCachePort.CacheEntry(user.getId(), family)));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> new RefreshTokenUseCase(tokens, refresh, cache, users, revoker)
                .execute("old-refresh", "127.0.0.1", "test", null))
                .isInstanceOf(AuthException.class).hasMessage("Email verification required");
        verify(tokens, never()).generateAccessToken(any(), anyString());
        verify(tokens, never()).generateRefreshToken(any(), any(), any());
        verifyNoInteractions(refresh);
        verify(cache, never()).blacklist(anyString(), any());
        verify(cache, never()).store(anyString(), any(), any(), any());
    }
}
