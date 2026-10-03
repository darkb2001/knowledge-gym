package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class BlockedAccountAuthTest {
    @Test void localLoginCannotIssueTokensForBlockedAccount() {
        var users = mock(UserRepository.class);
        var passwords = mock(PasswordHasher.class);
        var tokens = mock(TokenService.class);
        var refresh = mock(RefreshTokenRepository.class);
        var cache = mock(RefreshTokenCachePort.class);
        var user = new User("learner@example.com", "hash", "Learner");
        user.verifyEmail(); user.setBlocked(true);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwords.matches("password", "hash")).thenReturn(true);
        assertThrows(AuthException.class, () -> new LoginUseCase(users, passwords, tokens, refresh, cache)
                .execute(user.getEmail(), "password", null, null));
        verifyNoInteractions(tokens, refresh, cache);
    }
    @Test void refreshCannotIssueTokensForBlockedAccountEvenWithRedisHit() {
        var users = mock(UserRepository.class);
        var tokens = mock(TokenService.class);
        var refresh = mock(RefreshTokenRepository.class);
        var cache = mock(RefreshTokenCachePort.class);
        var user = new User("learner@example.com", "hash", "Learner");
        user.verifyEmail(); user.setBlocked(true);
        var family = UUID.randomUUID();
        when(tokens.verifyRefreshToken("raw")).thenReturn(new TokenService.RefreshTokenClaims(user.getId(), family));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(cache.find(HashUtils.sha256Hex("raw"))).thenReturn(
                Optional.of(new RefreshTokenCachePort.CacheEntry(user.getId(), family)));
        assertThrows(AuthException.class, () -> new RefreshTokenUseCase(tokens, refresh, cache, users)
                .execute("raw", null, null));
        verify(tokens, never()).generateAccessToken(any(), any());
        verify(refresh, never()).save(any());
    }
}
