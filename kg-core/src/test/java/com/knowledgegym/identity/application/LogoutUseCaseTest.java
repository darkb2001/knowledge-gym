package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.RefreshToken;
import com.knowledgegym.identity.domain.port.RefreshTokenCachePort;
import com.knowledgegym.identity.domain.port.RefreshTokenRepository;
import com.knowledgegym.identity.domain.port.SessionInvalidationPort;
import com.knowledgegym.identity.domain.port.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Logout phải revoke refresh family VÀ cắt access token đang lưu hành
 * (nếu chỉ revoke family thì JWT cũ vẫn gọi API được tới khi hết hạn 15 phút).
 */
class LogoutUseCaseTest {

    private final TokenService tokenService = mock(TokenService.class);
    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final RefreshTokenCachePort cache = mock(RefreshTokenCachePort.class);
    private final SessionInvalidationPort sessionInvalidation = mock(SessionInvalidationPort.class);

    private final UUID userId = UUID.randomUUID();
    private final UUID familyId = UUID.randomUUID();
    private final String raw = "refresh.raw.token";
    private final String hash = HashUtils.sha256Hex(raw);

    private LogoutUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LogoutUseCase(tokenService, repository, cache, sessionInvalidation);
    }

    @Test
    void validTokenRevokesFamilyAndCutsIssuedAccessTokens() {
        when(tokenService.verifyRefreshToken(raw)).thenReturn(new TokenService.RefreshTokenClaims(userId, familyId));

        useCase.execute(raw);

        verify(repository).revokeFamily(familyId);
        verify(cache).revokeFamily(familyId, RefreshToken.TTL);
        verify(cache).blacklist(hash, RefreshToken.TTL);
        verify(sessionInvalidation).invalidateIssuedBefore(eq(userId), any(Instant.class));
    }

    @Test
    void unparseableTokenFallsBackToAuditRowAndStillCutsAccessTokens() {
        when(tokenService.verifyRefreshToken(raw)).thenThrow(new IllegalArgumentException("expired"));
        when(repository.findByTokenHash(hash)).thenReturn(Optional.of(new RefreshToken(userId, hash, familyId)));

        useCase.execute(raw);

        verify(repository).revokeFamily(familyId);
        verify(sessionInvalidation).invalidateIssuedBefore(eq(userId), any(Instant.class));
    }

    @Test
    void unknownTokenTouchesNothing() {
        when(tokenService.verifyRefreshToken(raw)).thenThrow(new IllegalArgumentException("expired"));
        when(repository.findByTokenHash(hash)).thenReturn(Optional.empty());

        useCase.execute(raw);

        verify(repository, never()).revokeFamily(any());
        verifyNoInteractions(cache, sessionInvalidation);
    }
}
