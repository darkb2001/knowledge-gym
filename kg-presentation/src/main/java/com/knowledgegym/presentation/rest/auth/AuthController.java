package com.knowledgegym.presentation.rest.auth;

import com.knowledgegym.identity.application.*;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.infrastructure.security.ClientIpResolver;
import com.knowledgegym.infrastructure.security.RefreshTokenCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for /auth/* endpoints — gọi use case trong kg-core (DDD application layer).
 * Refresh token set qua httpOnly Secure SameSite=Strict cookie (RefreshTokenCookie helper).
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final long ACCESS_TOKEN_TTL_SECONDS = 900L; // 15 minutes
    private static final int USER_AGENT_MAX_LEN = 500;

    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final ForgotPasswordUseCase forgotPasswordUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final RefreshTokenCookie refreshCookie;
    private final ClientIpResolver clientIpResolver;

    public AuthController(RegisterUseCase registerUseCase,
                           LoginUseCase loginUseCase,
                           RefreshTokenUseCase refreshTokenUseCase,
                           LogoutUseCase logoutUseCase,
                           ForgotPasswordUseCase forgotPasswordUseCase,
                           ResetPasswordUseCase resetPasswordUseCase,
                           RefreshTokenCookie refreshCookie,
                           ClientIpResolver clientIpResolver) {
        this.registerUseCase = registerUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.forgotPasswordUseCase = forgotPasswordUseCase;
        this.resetPasswordUseCase = resetPasswordUseCase;
        this.refreshCookie = refreshCookie;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest req,
                                   HttpServletRequest httpRequest, HttpServletResponse response) {
        User user = registerUseCase.execute(req.email(), req.password(), req.displayName());
        LoginUseCase.AuthResult auth = loginUseCase.execute(user.getEmail(), req.password(),
                clientIpResolver.resolve(httpRequest),
                clientIpResolver.userAgent(httpRequest, USER_AGENT_MAX_LEN));
        refreshCookie.write(response, auth.refreshToken());
        return buildTokenResponse(auth, user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name());
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req,
                                HttpServletRequest httpRequest, HttpServletResponse response) {
        LoginUseCase.AuthResult auth = loginUseCase.execute(req.email(), req.password(),
                clientIpResolver.resolve(httpRequest),
                clientIpResolver.userAgent(httpRequest, USER_AGENT_MAX_LEN));
        refreshCookie.write(response, auth.refreshToken());
        return new TokenResponse(auth.accessToken(), ACCESS_TOKEN_TTL_SECONDS,
                new TokenResponse.UserResponse(auth.userId().toString(), req.email(),
                        auth.displayName(), auth.role()));
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh(HttpServletRequest httpRequest, HttpServletResponse response) {
        String rawRefresh = refreshCookie.read(httpRequest);
        if (rawRefresh == null) throw new AuthException("Missing refresh cookie");
        RefreshTokenUseCase.Result result = refreshTokenUseCase.execute(rawRefresh,
                clientIpResolver.resolve(httpRequest),
                clientIpResolver.userAgent(httpRequest, USER_AGENT_MAX_LEN));
        refreshCookie.write(response, result.newRefreshToken());
        return Map.of("accessToken", result.accessToken(), "expiresIn", ACCESS_TOKEN_TTL_SECONDS);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest, HttpServletResponse response) {
        String rawRefresh = refreshCookie.read(httpRequest);
        if (rawRefresh != null) logoutUseCase.execute(rawRefresh);
        refreshCookie.clear(response);
    }

    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        String msg = forgotPasswordUseCase.execute(req.email());
        return Map.of("message", msg);
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        resetPasswordUseCase.execute(req.email(), req.code(), req.newPassword());
        return Map.of("message", "Đặt lại mật khẩu thành công");
    }

    private TokenResponse buildTokenResponse(LoginUseCase.AuthResult auth, java.util.UUID userId,
                                              String email, String displayName, String role) {
        return new TokenResponse(auth.accessToken(), ACCESS_TOKEN_TTL_SECONDS,
                new TokenResponse.UserResponse(userId.toString(), email, displayName, role));
    }
}
