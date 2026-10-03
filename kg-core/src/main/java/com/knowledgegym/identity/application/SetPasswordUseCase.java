package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.PasswordResetCode;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.EmailService;
import com.knowledgegym.identity.domain.port.PasswordHasher;
import com.knowledgegym.identity.domain.port.PasswordResetCodeRepository;
import com.knowledgegym.identity.domain.port.SecurityEventPort;
import com.knowledgegym.identity.domain.port.UserRepository;

import java.util.UUID;

/**
 * "Đặt mật khẩu" lần đầu cho tài khoản đăng nhập bằng Google (`password_hash IS NULL`) — luồng chuẩn
 * của các app OAuth lớn (Notion/Slack/Atlassian): user đã đăng nhập vẫn phải xác minh mã 6 số gửi về
 * email trước khi đặt mật khẩu, nên không ai chiếm được phiên rồi tự đặt mật khẩu.
 *
 * Sau khi đặt: tài khoản đăng nhập được CẢ Google lẫn email + mật khẩu (auth_provider giữ nguyên).
 * KHÔNG thu hồi phiên — người dùng đang đăng nhập bằng Google không bị đá ra.
 */
public class SetPasswordUseCase {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 72;

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final PasswordHasher passwordHasher;
    private final EmailService emailService;
    private final SecurityEventPort securityEvents;

    public SetPasswordUseCase(UserRepository userRepository,
                              PasswordResetCodeRepository codeRepository,
                              PasswordHasher passwordHasher,
                              EmailService emailService,
                              SecurityEventPort securityEvents) {
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.passwordHasher = passwordHasher;
        this.emailService = emailService;
        this.securityEvents = securityEvents;
    }

    /** Mã 6 số gửi qua POST /auth/password-code/request (email lấy từ phiên đăng nhập, không từ body). */
    public void execute(UUID userId, String code, String newPassword, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthException.Kind.UNAUTHORIZED,
                        "Phiên đăng nhập không hợp lệ"));

        if (user.getPasswordHash() != null) {
            throw new AuthException(AuthException.Kind.CONFLICT,
                    "Tài khoản đã có mật khẩu — hãy dùng chức năng đổi mật khẩu");
        }
        if (newPassword == null || newPassword.length() < MIN_LENGTH || newPassword.length() > MAX_LENGTH) {
            throw new AuthException(AuthException.Kind.BAD_REQUEST,
                    "Mật khẩu phải từ " + MIN_LENGTH + " đến " + MAX_LENGTH + " ký tự");
        }

        PasswordResetCode stored = PasswordResetCodeVerifier.verify(codeRepository, user.getId(), code);

        // Mã hợp lệ ⇒ chứng minh sở hữu hộp thư của chính tài khoản này.
        user.verifyEmail();
        user.changePassword(passwordHasher.hash(newPassword));
        userRepository.save(user);

        stored.markUsed();
        codeRepository.save(stored);

        securityEvents.passwordSet(user.getId(), ipAddress);

        try {
            emailService.sendPasswordChangedNotice(user.getEmail());
        } catch (RuntimeException ignored) {
            // best-effort
        }
    }
}
