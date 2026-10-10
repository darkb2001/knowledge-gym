package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.port.RefreshTokenCachePort;
import com.knowledgegym.identity.domain.port.RefreshTokenRepository;
import com.knowledgegym.identity.domain.port.SessionInvalidationPort;
import com.knowledgegym.identity.domain.port.SessionRegistryPort;
import com.knowledgegym.identity.domain.port.TokenService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Quản lý thiết bị đăng nhập: mỗi login là một family riêng nên nhiều thiết bị sống song song.
 * Các test dưới đây khoá lại 3 tính chất quan trọng:
 *  1. chỉ revoke được family của chính mình (không đụng thiết bị người khác),
 *  2. "đăng xuất thiết bị khác" giữ nguyên phiên hiện tại,
 *  3. mọi lần revoke đều cắt access token đang lưu hành (JWT stateless, không cắt thì sống thêm 15').
 */
class SessionManagementUseCaseTest {

    private final SessionRegistryPort registry = mock(SessionRegistryPort.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final RefreshTokenCachePort cache = mock(RefreshTokenCachePort.class);
    private final SessionInvalidationPort sessionInvalidation = mock(SessionInvalidationPort.class);
    private final TokenService tokenService = mock(TokenService.class);

    private final SessionManagementUseCase useCase = new SessionManagementUseCase(
            registry, refreshTokenRepository, cache, sessionInvalidation, tokenService);

    private final UUID user = UUID.randomUUID();
    private final UUID phone = UUID.randomUUID();
    private final UUID desktop = UUID.randomUUID();

    private SessionRegistryPort.SessionSummary session(UUID family) {
        Instant now = Instant.now();
        return new SessionRegistryPort.SessionSummary(family, now, now, "1.2.3.4", "agent");
    }

    private void tokenBelongsTo(String raw, UUID family) {
        when(tokenService.verifyRefreshToken(raw))
                .thenReturn(new TokenService.RefreshTokenClaims(user, family, null));
    }

    @Test void listMarksCurrentSessionAndKeepsOthersUsable() {
        when(registry.listActive(user)).thenReturn(List.of(session(phone), session(desktop)));
        tokenBelongsTo("cookie-phone", phone);

        var views = useCase.list(user, "cookie-phone");

        assertEquals(2, views.size());
        assertEquals(1, views.stream().filter(SessionManagementUseCase.SessionView::current).count());
        assertEquals(phone.toString(),
                views.stream().filter(SessionManagementUseCase.SessionView::current).findFirst().orElseThrow().familyId());
    }

    @Test void listDoesNotMarkForeignSessionAsCurrent() {
        when(registry.listActive(user)).thenReturn(List.of(session(phone)));
        // cookie của người khác (userId khác) thì không được coi là phiên hiện tại
        when(tokenService.verifyRefreshToken("cookie-other"))
                .thenReturn(new TokenService.RefreshTokenClaims(UUID.randomUUID(), phone, null));

        var views = useCase.list(user, "cookie-other");

        assertTrue(views.stream().noneMatch(SessionManagementUseCase.SessionView::current));
    }

    @Test void revokeRejectsFamilyOfAnotherUser() {
        when(registry.listActive(user)).thenReturn(List.of(session(phone)));

        var result = useCase.revoke(user, desktop, "cookie-phone");

        assertFalse(result.removed());
        verifyNoInteractions(refreshTokenRepository, cache, sessionInvalidation);
    }

    @Test void revokeOwnSessionCutsLiveAccessTokens() {
        when(registry.listActive(user)).thenReturn(List.of(session(phone), session(desktop)));
        tokenBelongsTo("cookie-desktop", desktop);

        var result = useCase.revoke(user, phone, "cookie-desktop");

        assertTrue(result.removed());
        assertFalse(result.wasCurrent());
        verify(refreshTokenRepository).revokeFamily(phone);
        verify(cache).revokeFamily(eq(phone), any());
        verify(sessionInvalidation).invalidateIssuedBefore(eq(user), any());
    }

    @Test void revokeCurrentSessionReportsItSoCookieCanBeCleared() {
        when(registry.listActive(user)).thenReturn(List.of(session(phone)));
        tokenBelongsTo("cookie-phone", phone);

        var result = useCase.revoke(user, phone, "cookie-phone");

        assertTrue(result.removed());
        assertTrue(result.wasCurrent());
    }

    @Test void revokeOthersKeepsCurrentSessionAlive() {
        when(registry.listActive(user)).thenReturn(List.of(session(phone), session(desktop)));
        tokenBelongsTo("cookie-phone", phone);

        int revoked = useCase.revokeOthers(user, "cookie-phone");

        assertEquals(1, revoked);
        verify(refreshTokenRepository).revokeFamily(desktop);
        verify(refreshTokenRepository, never()).revokeFamily(phone);
        verify(cache, never()).revokeFamily(eq(phone), any());
    }

    @Test void revokeOthersWithoutCookieRevokesEverything() {
        when(registry.listActive(user)).thenReturn(List.of(session(phone), session(desktop)));
        when(tokenService.verifyRefreshToken("bad")).thenThrow(new RuntimeException("invalid"));

        int revoked = useCase.revokeOthers(user, "bad");

        assertEquals(2, revoked);
        verify(refreshTokenRepository).revokeFamily(phone);
        verify(refreshTokenRepository).revokeFamily(desktop);
    }
}
