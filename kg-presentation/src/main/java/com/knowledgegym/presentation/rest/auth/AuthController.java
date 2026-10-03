package com.knowledgegym.presentation.rest.auth;

import com.knowledgegym.identity.application.*;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.infrastructure.security.ClientIpResolver;
import com.knowledgegym.infrastructure.security.RefreshTokenCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    private final VerifiedRegistrationUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final ForgotPasswordUseCase forgotPasswordUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;
    private final SetPasswordUseCase setPasswordUseCase;
    private final GetCurrentUserUseCase currentUser;
    private final RefreshTokenCookie refreshCookie;
    private final ClientIpResolver clientIpResolver;

    public AuthController(VerifiedRegistrationUseCase registerUseCase,
                           LoginUseCase loginUseCase,
                           RefreshTokenUseCase refreshTokenUseCase,
                           LogoutUseCase logoutUseCase,
                           ForgotPasswordUseCase forgotPasswordUseCase,
                           ResetPasswordUseCase resetPasswordUseCase,
                           ChangePasswordUseCase changePasswordUseCase,
                           SetPasswordUseCase setPasswordUseCase,
                           GetCurrentUserUseCase currentUser,
                           RefreshTokenCookie refreshCookie,
                           ClientIpResolver clientIpResolver) {
        this.registerUseCase = registerUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.forgotPasswordUseCase = forgotPasswordUseCase;
        this.resetPasswordUseCase = resetPasswordUseCase;
        this.changePasswordUseCase = changePasswordUseCase;
        this.setPasswordUseCase = setPasswordUseCase;
        this.currentUser = currentUser;
        this.refreshCookie = refreshCookie;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest req,
                                   HttpServletRequest httpRequest, HttpServletResponse response) {
        User user = registerUseCase.register(req.email(), req.password(), req.displayName(), req.verificationCode());
        LoginUseCase.AuthResult auth = loginUseCase.execute(user.getEmail(), req.password(),
                clientIpResolver.resolve(httpRequest),
                clientIpResolver.userAgent(httpRequest, USER_AGENT_MAX_LEN));
        refreshCookie.write(response, auth.refreshToken());
        return buildTokenResponse(auth, user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name());
    }

    public record VerificationEmailRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email) {}

    public record VerifyEmailRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email,
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Pattern(regexp = "[0-9]{6}") String code,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 8, max = 72) String newPassword,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 8, max = 72) String confirmPassword) {
        @jakarta.validation.constraints.AssertTrue(message = "Passwords do not match")
        public boolean isPasswordConfirmed() {
            return newPassword != null && newPassword.equals(confirmPassword);
        }

        @Override
        public String toString() {
            return "VerifyEmailRequest[email=" + email + ", code=[REDACTED], passwords=[REDACTED]]";
        }
    }

    @PostMapping("/email-verification/request")
    public Map<String, String> requestVerification(@Valid @RequestBody VerificationEmailRequest req) {
        registerUseCase.requestCode(req.email());
        return Map.of("message", "Nếu yêu cầu hợp lệ, mã xác minh đã được gửi. Mã có hiệu lực 10 phút.");
    }

    @PostMapping("/verify-email")
    public Map<String, String> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        registerUseCase.verifyExisting(req.email(), req.code(), req.newPassword());
        return Map.of("message", "Email đã xác minh. Vui lòng đăng nhập.");
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

    // ----------------------------------------------------- đổi / đặt mật khẩu khi ĐÃ đăng nhập

    public record ChangePasswordRequest(
            @jakarta.validation.constraints.NotBlank String currentPassword,
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(min = 8, max = 72) String newPassword,
            @jakarta.validation.constraints.NotBlank String confirmPassword) {
        @jakarta.validation.constraints.AssertTrue(message = "Passwords do not match")
        public boolean isPasswordConfirmed() {
            return newPassword != null && newPassword.equals(confirmPassword);
        }

        @Override
        public String toString() {
            return "ChangePasswordRequest[passwords=[REDACTED]]";
        }
    }

    public record SetPasswordRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Pattern(regexp = "[0-9]{6}") String code,
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(min = 8, max = 72) String newPassword,
            @jakarta.validation.constraints.NotBlank String confirmPassword) {
        @jakarta.validation.constraints.AssertTrue(message = "Passwords do not match")
        public boolean isPasswordConfirmed() {
            return newPassword != null && newPassword.equals(confirmPassword);
        }

        @Override
        public String toString() {
            return "SetPasswordRequest[code=[REDACTED], passwords=[REDACTED]]";
        }
    }

    /**
     * Gửi mã 6 số để ĐẶT mật khẩu lần đầu (tài khoản Google chưa có mật khẩu).
     * Email lấy từ phiên đăng nhập — không nhận email từ body (tránh gửi mã cho người khác).
     */
    @PostMapping("/password-code/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> requestPasswordCode(@AuthenticationPrincipal java.util.UUID userId) {
        requireAuthenticated(userId);
        String email = currentUser.execute(userId).getEmail();
        return Map.of("message", forgotPasswordUseCase.execute(email));
    }

    /**
     * Đổi mật khẩu (đã đăng nhập): mật khẩu hiện tại + mật khẩu mới + xác nhận.
     * Giữ phiên hiện tại, đăng xuất mọi phiên khác, ghi audit + gửi mail thông báo.
     */
    @PostMapping("/change-password")
    public Map<String, String> changePassword(@AuthenticationPrincipal java.util.UUID userId,
                                              @Valid @RequestBody ChangePasswordRequest req,
                                              HttpServletRequest httpRequest) {
        requireAuthenticated(userId);
        changePasswordUseCase.execute(userId, req.currentPassword(), req.newPassword(),
                refreshCookie.read(httpRequest), clientIpResolver.resolve(httpRequest));
        return Map.of("message", "Đổi mật khẩu thành công. Các thiết bị khác đã được đăng xuất.");
    }

    /**
     * Đặt mật khẩu lần đầu cho tài khoản OAuth (Google) — cần mã 6 số gửi về email.
     * Sau bước này tài khoản đăng nhập được cả Google lẫn email + mật khẩu.
     */
    @PostMapping("/set-password")
    public Map<String, String> setPassword(@AuthenticationPrincipal java.util.UUID userId,
                                           @Valid @RequestBody SetPasswordRequest req,
                                           HttpServletRequest httpRequest) {
        requireAuthenticated(userId);
        setPasswordUseCase.execute(userId, req.code(), req.newPassword(),
                clientIpResolver.resolve(httpRequest));
        return Map.of("message", "Đặt mật khẩu thành công. Bạn có thể đăng nhập bằng email và mật khẩu này.");
    }

    private static void requireAuthenticated(java.util.UUID userId) {
        if (userId == null) {
            throw new AuthException(AuthException.Kind.UNAUTHORIZED, "Yêu cầu đăng nhập");
        }
    }

    private TokenResponse buildTokenResponse(LoginUseCase.AuthResult auth, java.util.UUID userId,
                                              String email, String displayName, String role) {
        return new TokenResponse(auth.accessToken(), ACCESS_TOKEN_TTL_SECONDS,
                new TokenResponse.UserResponse(userId.toString(), email, displayName, role));
    }
}
