package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.EmailService;
import com.knowledgegym.identity.domain.port.EmailVerificationPort;
import com.knowledgegym.identity.domain.port.UserRepository;
import com.knowledgegym.identity.domain.port.PasswordHasher;
import com.knowledgegym.identity.domain.port.RefreshTokenRepository;
import com.knowledgegym.identity.domain.port.RefreshTokenCachePort;
import com.knowledgegym.identity.domain.model.RefreshToken;
import java.security.SecureRandom;

/** Verify ownership before creating a local account; registration always creates USER. */
public final class VerifiedRegistrationUseCase {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final RegisterUseCase registration;
    private final UserRepository users;
    private final EmailVerificationPort challenges;
    private final EmailService mail;
    private final PasswordHasher passwords;
    private final RefreshTokenRepository refreshTokens;
    private final RefreshTokenCachePort cache;

    public VerifiedRegistrationUseCase(RegisterUseCase registration, UserRepository users,
                                      EmailVerificationPort challenges, EmailService mail,
                                      PasswordHasher passwords, RefreshTokenRepository refreshTokens,
                                      RefreshTokenCachePort cache) {
        this.registration = registration;
        this.users = users;
        this.challenges = challenges;
        this.mail = mail;
        this.passwords = passwords;
        this.refreshTokens = refreshTokens;
        this.cache = cache;
    }

    public void requestCode(String email) {
        String normalized = User.normalizeEmail(email);
        // Same challenge flow can verify an existing unverified local account.
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String hash = HashUtils.sha256Hex(code);
        if (!challenges.issue(normalized, hash)) return;
        try {
            mail.sendEmailVerificationCode(normalized, code);
        } catch (RuntimeException ex) {
            challenges.invalidate(normalized, hash);
            throw ex;
        }
    }

    public void verifyExisting(String email, String code, String newPassword) {
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 72) {
            throw new AuthException(AuthException.Kind.BAD_REQUEST, "Password must be 8–72 characters");
        }
        String normalized = User.normalizeEmail(email);
        consumeCode(normalized, code);
        User user = users.findByEmail(normalized)
                .orElseThrow(() -> new AuthException(AuthException.Kind.BAD_REQUEST, "Invalid verification request"));
        // Never unlock a legacy account using its potentially attacker-chosen password.
        // Verified users should use the existing password reset flow instead.
        if (user.isEmailVerified()) {
            throw new AuthException(AuthException.Kind.BAD_REQUEST, "Email already verified; sign in or reset password");
        }
        user.changePassword(passwords.hash(newPassword));
        user.verifyEmail();
        users.save(user);
        for (var family : refreshTokens.findActiveFamilyIdsByUserId(user.getId())) {
            cache.revokeFamily(family, RefreshToken.TTL);
        }
        refreshTokens.revokeAllByUserId(user.getId());
    }

    private void consumeCode(String normalized, String code) {
        if (code == null || !code.matches("[0-9]{6}")
                || !challenges.consume(normalized, HashUtils.sha256Hex(code))) {
            throw new AuthException(AuthException.Kind.BAD_REQUEST, "Invalid or expired email verification code");
        }
    }

    public User register(String email, String password, String displayName, String code) {
        String normalized = User.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw new AuthException(AuthException.Kind.CONFLICT, "Account already exists; sign in instead");
        }
        consumeCode(normalized, code);
        User user = registration.execute(normalized, password, displayName);
        user.verifyEmail();
        return users.save(user);
    }
}
