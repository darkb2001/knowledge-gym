package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.PasswordResetCode;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.EmailService;
import com.knowledgegym.identity.domain.port.PasswordResetCodeRepository;
import com.knowledgegym.identity.domain.port.UserRepository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

public class ForgotPasswordUseCase {

    /**
     * Per-user cooldown: tối đa 1 code mới/phút/email.
     * Rate limit 3/min/IP không chống được botnet (nhiều IP) — cooldown theo TARGET
     * email chặn dồn code để brute-force (mỗi code chỉ có 5 lần thử).
     */
    private static final Duration COOLDOWN = Duration.ofMinutes(1);

    private static final SecureRandom RNG = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository repository;
    private final EmailService emailService;

    public ForgotPasswordUseCase(UserRepository userRepository,
                                 PasswordResetCodeRepository repository,
                                 EmailService emailService) {
        this.userRepository = userRepository;
        this.repository = repository;
        this.emailService = emailService;
    }

    /**
     * Trả message chung dù email có tồn tại hay không — tránh email enumeration.
     */
    public String execute(String email) {
        String normalized = User.normalizeEmail(email);
        userRepository.findByEmail(normalized).ifPresent(user -> {
            // Cooldown: code gần nhất còn hạn và mới tạo < 60s → bỏ qua (vẫn trả message chung)
            var latest = repository.findLatestActiveByUserId(user.getId());
            if (latest.isPresent()
                    && latest.get().getCreatedAt().isAfter(Instant.now().minus(COOLDOWN))) {
                return;
            }
            // Chỉ giữ 1 code active: invalidate code cũ trước khi tạo code mới
            latest.ifPresent(old -> {
                old.markUsed();
                repository.save(old);
            });

            String code = String.format("%06d", RNG.nextInt(1_000_000));
            String hash = HashUtils.sha256Hex(code);
            PasswordResetCode pending = new PasswordResetCode(user.getId(), hash);
            repository.save(pending);
            try {
                emailService.sendPasswordResetCode(user.getEmail(), code);
            } catch (RuntimeException deliveryFailure) {
                // Do not leave a usable reset code when the outbox/SMTP handoff failed.
                pending.markUsed();
                repository.save(pending);
                throw deliveryFailure;
            }
        });
        return "Đã gửi mã tới email";
    }
}