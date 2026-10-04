package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.PasswordResetCode;
import com.knowledgegym.identity.domain.model.RefreshToken;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.*;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public class ResetPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final PasswordHasher passwordHasher;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCachePort cache;
    private final SessionInvalidationPort sessionInvalidation;

    public ResetPasswordUseCase(UserRepository userRepository,
                                 PasswordResetCodeRepository codeRepository,
                                 PasswordHasher passwordHasher,
                                 RefreshTokenRepository refreshTokenRepository,
                                 RefreshTokenCachePort cache,
                                 SessionInvalidationPort sessionInvalidation) {
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.passwordHasher = passwordHasher;
        this.refreshTokenRepository = refreshTokenRepository;
        this.cache = cache;
        this.sessionInvalidation = sessionInvalidation;
    }

    /**
     * Xác nhận mã 6 chữ số → update password (BCrypt) → revoke TẤT CẢ refresh token
     * (cả PostgreSQL audit lẫn Redis cache — Redis-first lookup không thể bypass).
     * Sai code → +1 attempts; quá 5 lần → invalidate (markUsed).
     */
    public void execute(String email, String code, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new AuthException(AuthException.Kind.BAD_REQUEST,
                    "Password must be at least 8 characters");
        }
        String normalized = User.normalizeEmail(email);
        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new AuthException(AuthException.Kind.BAD_REQUEST, "Invalid reset code"));

        PasswordResetCode stored = codeRepository.findLatestActiveByUserId(user.getId())
                .orElseThrow(() -> new AuthException(AuthException.Kind.BAD_REQUEST, "Invalid reset code"));

        if (stored.isExpired() || stored.isUsed() || stored.isAttemptsExhausted()) {
            throw new AuthException(AuthException.Kind.BAD_REQUEST, "Reset code expired or already used");
        }

        String submittedHash = HashUtils.sha256Hex(code);
        if (!submittedHash.equals(stored.getCodeHash())) {
            stored.incrementAttempts();
            if (stored.isAttemptsExhausted()) {
                stored.markUsed();
            }
            codeRepository.save(stored);
            throw new AuthException(AuthException.Kind.BAD_REQUEST, "Invalid reset code");
        }

        // Success: update password (domain method gọi touch()) + single-use code
        // A valid reset code also proves ownership of this account's mailbox.
        user.verifyEmail();
        user.changePassword(passwordHasher.hash(newPassword));
        userRepository.save(user);

        stored.markUsed();
        codeRepository.save(stored);

        // Revoke toàn bộ refresh token family của user — mọi session cũ bị đá.
        // Capture families TRƯỚC khi revoke PG (sau revoke thì query active trả về rỗng).
        Set<UUID> activeFamilies = refreshTokenRepository.findActiveFamilyIdsByUserId(user.getId());
        for (UUID familyId : activeFamilies) {
            cache.revokeFamily(familyId, RefreshToken.TTL);
        }
        refreshTokenRepository.revokeAllByUserId(user.getId());
        // Đặt lại mật khẩu thường do mất quyền kiểm soát tài khoản ⇒ cắt luôn access token cũ (JWT
        // stateless). Không ảnh hưởng phiên hiện tại vì người dùng chưa đăng nhập ở luồng này.
        sessionInvalidation.invalidateIssuedBefore(user.getId(), Instant.now());
    }
}