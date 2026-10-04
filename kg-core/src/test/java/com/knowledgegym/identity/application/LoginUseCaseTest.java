package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.*;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Login bằng mật khẩu trên tài khoản chỉ có OAuth (Google) phải trả mã lỗi RIÊNG
 * ("oauth_only_account") thay vì "Invalid email or password": người dùng đăng ký bằng Google rồi
 * thử mật khẩu sẽ gõ lại mật khẩu vô ích, còn FE cần biết để mời họ bấm nút Google.
 */
class LoginUseCaseTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
    private final TokenService tokenService = mock(TokenService.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final RefreshTokenCachePort cache = mock(RefreshTokenCachePort.class);

    private final LoginUseCase useCase = new LoginUseCase(userRepository, passwordHasher,
            tokenService, refreshTokenRepository, cache);

    private User googleUser() {
        User user = User.createGoogleUser("huiencute1911@gmail.com", "Huien", "google-sub-123");
        user.verifyEmail();
        return user;
    }

    @Test void oauthOnlyAccountGetsDedicatedErrorAndNoTokens() {
        User user = googleUser();
        assertNull(user.getPasswordHash(), "tiền đề: tài khoản Google không có hash mật khẩu");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));

        AuthException error = assertThrows(AuthException.class,
                () -> useCase.execute("huiencute1911@gmail.com", "matkhaubatky", "1.2.3.4", "test-agent"));

        assertEquals("oauth_only_account", error.getMessage());
        verifyNoInteractions(tokenService, refreshTokenRepository, cache);
        verify(passwordHasher, never()).matches(any(), any());
    }

    @Test void wrongPasswordStillGenericMessage() {
        User user = googleUser();
        user.setPasswordHash("$2a$10$hash");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(passwordHasher.matches(any(), any())).thenReturn(false);

        AuthException error = assertThrows(AuthException.class,
                () -> useCase.execute("huiencute1911@gmail.com", "sai", "1.2.3.4", "test-agent"));

        assertEquals("Invalid email or password", error.getMessage());
        verifyNoInteractions(tokenService, refreshTokenRepository, cache);
    }

    @Test void validPasswordIssuesTokens() {
        User user = googleUser();
        user.setPasswordHash("$2a$10$hash");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(passwordHasher.matches(any(), any())).thenReturn(true);
        when(tokenService.generateAccessToken(any(), anyString())).thenReturn("access.jwt");
        when(tokenService.generateRefreshToken(any(), any())).thenReturn("refresh.raw");

        var result = useCase.execute("huiencute1911@gmail.com", "dung", "1.2.3.4", "test-agent");

        assertEquals("access.jwt", result.accessToken());
        assertEquals("refresh.raw", result.refreshToken());
        verify(refreshTokenRepository).save(any());
        verify(cache).store(anyString(), any(), any(), any());
    }
}
