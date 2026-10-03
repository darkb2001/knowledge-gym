package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.RefreshToken;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.*;

import java.util.Set;
import java.util.UUID;

/**
 * Đổi mật khẩu cho người dùng ĐÃ đăng nhập — theo chuẩn GitHub/Google/Atlassian:
 *  - phải nhập đúng mật khẩu hiện tại (không dùng email link → tránh chiếm phiên),
 *  - mật khẩu mới tối thiểu 8 ký tự, tối đa 72 (giới hạn BCrypt),
 *  - không cho đặt lại chính mật khẩu cũ,
 *  - thu hồi mọi phiên KHÁC nhưng giữ phiên đang thao tác (người dùng không bị đá ra),
 *  - ghi audit + gửi mail thông báo (best-effort).
 *
 * Tài khoản OAuth chưa từng có mật khẩu (`password_hash IS NULL`) → CONFLICT để client chuyển
 * sang luồng "đặt mật khẩu" (SetPasswordUseCase) thay vì báo lỗi khó hiểu.
 */
public class ChangePasswordUseCase {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 72;

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCachePort cache;
    private final EmailService emailService;
    private final SecurityEventPort securityEvents;

    public ChangePasswordUseCase(UserRepository userRepository,
                                 PasswordHasher passwordHasher,
                                 RefreshTokenRepository refreshTokenRepository,
                                 RefreshTokenCachePort cache,
                                 EmailService emailService,
                                 SecurityEventPort securityEvents) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.refreshTokenRepository = refreshTokenRepository;
        this.cache = cache;
        this.emailService = emailService;
        this.securityEvents = securityEvents;
    }

    /**
     * @param currentRefreshToken raw refresh token của phiên đang gọi (cookie) — chỉ để GIỮ phiên này;
     *                            null thì thu hồi tất cả (an toàn hơn bỏ sót).
     */
    public void execute(UUID userId, String currentPassword, String newPassword,
                        String currentRefreshToken, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthException.Kind.UNAUTHORIZED,
                        "Phiên đăng nhập không hợp lệ"));

        if (user.getPasswordHash() == null) {
            throw new AuthException(AuthException.Kind.CONFLICT,
                    "Tài khoản này chưa có mật khẩu — hãy dùng chức năng đặt mật khẩu");
        }
        validateNewPassword(newPassword, user.getPasswordHash());

        if (currentPassword == null || !passwordHasher.matches(currentPassword, user.getPasswordHash())) {
            throw new AuthException(AuthException.Kind.BAD_REQUEST, "Mật khẩu hiện tại không đúng");
        }

        user.changePassword(passwordHasher.hash(newPassword));
        userRepository.save(user);

        revokeOtherSessions(user.getId(), currentRefreshToken);

        securityEvents.passwordChanged(user.getId(), ipAddress);
        notifySilently(user.getEmail());
    }

    private void validateNewPassword(String newPassword, String currentHash) {
        if (newPassword == null || newPassword.length() < MIN_LENGTH || newPassword.length() > MAX_LENGTH) {
            throw new AuthException(AuthException.Kind.BAD_REQUEST,
                    "Mật khẩu mới phải từ " + MIN_LENGTH + " đến " + MAX_LENGTH + " ký tự");
        }
        if (passwordHasher.matches(newPassword, currentHash)) {
            throw new AuthException(AuthException.Kind.BAD_REQUEST,
                    "Mật khẩu mới phải khác mật khẩu hiện tại");
        }
    }

    /** Giữ family của phiên hiện tại, revoke các family khác ở CẢ Redis lẫn PostgreSQL. */
    private void revokeOtherSessions(UUID userId, String currentRefreshToken) {
        UUID currentFamily = currentRefreshToken == null ? null
                : refreshTokenRepository.findByTokenHash(HashUtils.sha256Hex(currentRefreshToken))
                        .map(RefreshToken::getFamilyId)
                        .orElse(null);

        if (currentFamily == null) {
            Set<UUID> families = refreshTokenRepository.findActiveFamilyIdsByUserId(userId);
            for (UUID familyId : families) {
                cache.revokeFamily(familyId, RefreshToken.TTL);
            }
            refreshTokenRepository.revokeAllByUserId(userId);
            return;
        }

        Set<UUID> families = refreshTokenRepository.findActiveFamilyIdsByUserId(userId);
        for (UUID familyId : families) {
            if (familyId.equals(currentFamily)) {
                continue;
            }
            cache.revokeFamily(familyId, RefreshToken.TTL);
            refreshTokenRepository.revokeFamily(familyId);
        }
    }

    /** Mail thông báo không được phép làm fail thao tác đổi mật khẩu (SMTP có thể chết). */
    private void notifySilently(String email) {
        try {
            emailService.sendPasswordChangedNotice(email);
        } catch (RuntimeException ignored) {
            // best-effort
        }
    }
}
