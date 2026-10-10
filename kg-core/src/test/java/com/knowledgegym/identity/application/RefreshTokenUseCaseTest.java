package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.RefreshToken;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Regression guard cho lỗ hổng containment: nhánh reuse phải thu hồi family **bền vững**.
 *
 * <p>Bug gốc: {@code revokeFamily} nằm trong chính {@code @Transactional} rồi ném
 * {@code AuthException} → UPDATE bị rollback và Redis không hề được set, nên reuse detection chỉ
 * từ chối request chứ không cắt phiên. Test này khoá hợp đồng: revoker phải được gọi ở mọi nhánh
 * reuse, và repository dùng chung KHÔNG được gọi (nó sẽ bị rollback).
 */
class RefreshTokenUseCaseTest {

    private final TokenService tokens = mock(TokenService.class);
    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final RefreshTokenCachePort cache = mock(RefreshTokenCachePort.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RefreshFamilyRevoker revoker = mock(RefreshFamilyRevoker.class);

    private final UUID familyId = UUID.randomUUID();
    private final String raw = "refresh.raw";
    private final String hash = HashUtils.sha256Hex(raw);

    /** User thật (id ngẫu nhiên riêng) — claim phải mang đúng id này, không phải id bịa. */
    private final User activeUser = newActiveUser();

    private RefreshTokenUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RefreshTokenUseCase(tokens, repository, cache, users, revoker);
        // save() trả về entity đã có id (thực tế do JPA gán) — stub để rotation đọc được id.
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void verified() {
        when(tokens.verifyRefreshToken(raw))
                .thenReturn(new TokenService.RefreshTokenClaims(activeUser.getId(), familyId, Instant.now()));
    }

    private static User newActiveUser() {
        User user = new User("learner@example.com", "hash", "Learner");
        user.verifyEmail();
        return user;
    }

    @Test
    void blacklistedTokenRevokesFamilyDurablyAndNeverTouchesRollbackableRepository() {
        verified();
        when(cache.isBlacklisted(hash)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(raw, null, null, null))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("reuse detected");

        verify(revoker).revokeDurably(familyId);
        // revokeFamily trên repository sẽ bị rollback theo AuthException → không được dùng.
        verify(repository, never()).revokeFamily(any());
        verify(tokens, never()).generateAccessToken(any(), any());
    }

    @Test
    void alreadyRevokedFamilyRevokesDurablyBeforeThrowing() {
        verified();
        when(cache.isFamilyRevoked(familyId)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(raw, null, null, null))
                .isInstanceOf(AuthException.class);

        verify(revoker).revokeDurably(familyId);
        verify(repository, never()).revokeFamily(any());
    }

    @Test
    void revokedAuditRowWithoutRedisEntryIsTreatedAsReuse() {
        verified();
        RefreshToken revoked = new RefreshToken(activeUser.getId(), hash, familyId);
        revoked.revoke(UUID.randomUUID());
        when(repository.findByTokenHash(hash)).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> useCase.execute(raw, null, null, null))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("already revoked");

        verify(revoker).revokeDurably(familyId);
        verify(repository, never()).revokeFamily(any());
    }

    @Test
    void absoluteFamilyLifetimeRevokesDurablyEvenWhenTokenItselfIsFresh() {
        when(tokens.verifyRefreshToken(raw)).thenReturn(new TokenService.RefreshTokenClaims(
                activeUser.getId(), familyId, Instant.now().minus(Duration.ofDays(31))));
        when(cache.find(hash)).thenReturn(
                Optional.of(new RefreshTokenCachePort.CacheEntry(activeUser.getId(), familyId)));
        when(users.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> useCase.execute(raw, null, null, Duration.ofDays(30)))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("maximum lifetime");

        verify(revoker).revokeDurably(familyId);
        verify(tokens, never()).generateRefreshToken(any(), any(), any());
    }

    @Test
    void rotationPreservesOriginalFamilyAnchorInsteadOfResettingIt() {
        Instant originalAnchor = Instant.now().minus(Duration.ofDays(10));
        when(tokens.verifyRefreshToken(raw))
                .thenReturn(new TokenService.RefreshTokenClaims(activeUser.getId(), familyId, originalAnchor));
        when(cache.find(hash)).thenReturn(
                Optional.of(new RefreshTokenCachePort.CacheEntry(activeUser.getId(), familyId)));
        when(users.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(tokens.generateAccessToken(any(), anyString())).thenReturn("access");
        when(tokens.generateRefreshToken(any(), any(), any())).thenReturn("next.raw");
        RefreshToken old = new RefreshToken(activeUser.getId(), hash, familyId);
        when(repository.findByTokenHash(hash)).thenReturn(Optional.of(old));
        when(repository.revokeIfActive(any(), any())).thenReturn(true);

        useCase.execute(raw, "1.2.3.4", "agent", Duration.ofDays(30));

        ArgumentCaptor<Instant> anchor = ArgumentCaptor.forClass(Instant.class);
        verify(tokens).generateRefreshToken(eq(activeUser.getId()), eq(familyId), anchor.capture());
        // Mốc neo phải giữ nguyên, nếu reset về now thì absolute expiry vô hiệu.
        assertThat(anchor.getValue()).isEqualTo(originalAnchor);
    }

    @Test
    void rotationWithoutAbsoluteTtlKeepsLegacyBehaviour() {
        when(tokens.verifyRefreshToken(raw))
                .thenReturn(new TokenService.RefreshTokenClaims(activeUser.getId(), familyId, Instant.now()));
        when(cache.find(hash)).thenReturn(
                Optional.of(new RefreshTokenCachePort.CacheEntry(activeUser.getId(), familyId)));
        when(users.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(tokens.generateAccessToken(any(), anyString())).thenReturn("access");
        when(tokens.generateRefreshToken(any(), any(), any())).thenReturn("next.raw");
        RefreshToken old = new RefreshToken(activeUser.getId(), hash, familyId);
        when(repository.findByTokenHash(hash)).thenReturn(Optional.of(old));
        when(repository.revokeIfActive(any(), any())).thenReturn(true);

        var result = useCase.execute(raw, null, null, null);

        assertThat(result.newRefreshToken()).isEqualTo("next.raw");
        verify(revoker, never()).revokeDurably(any());
        verify(cache).blacklist(hash, RefreshToken.TTL);
    }

    @Test
    void concurrentLoserDoesNotRevokeFamilyBecauseWinnerStillOwnsSession() {
        verified();
        when(cache.find(hash)).thenReturn(
                Optional.of(new RefreshTokenCachePort.CacheEntry(activeUser.getId(), familyId)));
        when(users.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(tokens.generateAccessToken(any(), anyString())).thenReturn("access");
        when(tokens.generateRefreshToken(any(), any(), any())).thenReturn("next.raw");
        RefreshToken old = new RefreshToken(activeUser.getId(), hash, familyId);
        when(repository.findByTokenHash(hash)).thenReturn(Optional.of(old));
        when(repository.revokeIfActive(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(raw, null, null, null))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("already rotated");

        verify(revoker, never()).revokeDurably(any());
    }

    @Test
    void zeroOrNegativeAbsoluteTtlDisablesTheCap() {
        when(tokens.verifyRefreshToken(raw)).thenReturn(new TokenService.RefreshTokenClaims(
                activeUser.getId(), familyId, Instant.now().minus(Duration.ofDays(400))));
        when(cache.find(hash)).thenReturn(
                Optional.of(new RefreshTokenCachePort.CacheEntry(activeUser.getId(), familyId)));
        when(users.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(tokens.generateAccessToken(any(), anyString())).thenReturn("access");
        when(tokens.generateRefreshToken(any(), any(), any())).thenReturn("next.raw");
        RefreshToken old = new RefreshToken(activeUser.getId(), hash, familyId);
        when(repository.findByTokenHash(hash)).thenReturn(Optional.of(old));
        when(repository.revokeIfActive(any(), any())).thenReturn(true);

        // PT0S/âm = tắt policy: phiên 400 ngày vẫn rotate được (cấu hình để rollback nhanh).
        useCase.execute(raw, null, null, Duration.ZERO);
        useCase.execute(raw, null, null, Duration.ofDays(-1));

        verify(revoker, never()).revokeDurably(any());
    }

    @Test
    void revokeFailureIsLoggedButStillReturns401() {
        verified();
        when(cache.isBlacklisted(hash)).thenReturn(true);
        doThrow(new IllegalStateException("redis down")).when(revoker).revokeDurably(familyId);

        assertThatThrownBy(() -> useCase.execute(raw, null, null, null))
                .isInstanceOf(AuthException.class);
    }
}
