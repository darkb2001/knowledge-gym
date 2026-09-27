package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.PasswordResetCode;
import com.knowledgegym.identity.domain.model.RefreshToken;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.*;

import java.util.Set;
import java.util.UUID;

public class ResetPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final PasswordHasher passwordHasher;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCachePort cache;

    public ResetPasswordUseCase(UserRepository userRepository,
                                 PasswordResetCodeRepository codeRepository,
                                 PasswordHasher passwordHasher,
                                 RefreshTokenRepository refreshTokenRepository,
                                 RefreshTokenCachePort cache) {
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.passwordHasher = passwordHasher;
        this.refreshTokenRepository = refreshTokenRepository;
        this.cache = cache;
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
    }
}