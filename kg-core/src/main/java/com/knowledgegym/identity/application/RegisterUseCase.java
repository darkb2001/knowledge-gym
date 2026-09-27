package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.AuthProvider;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.PasswordHasher;
import com.knowledgegym.identity.domain.port.UserRepository;
import com.knowledgegym.shared.domain.model.UserRole;

public class RegisterUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public RegisterUseCase(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    /**
     * @return User đã persisted (id + createdAt populated).
     * @throws AuthException CONFLICT nếu email trùng (không leak email trong message).
     * @throws AuthException BAD_REQUEST nếu password &lt; 8 ký tự.
     */
    public User execute(String email, String password, String displayName) {
        String normalizedEmail = User.normalizeEmail(email);
        if (password == null || password.length() < 8) {
            throw new AuthException(AuthException.Kind.BAD_REQUEST,
                    "Password must be at least 8 characters");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            // Không nhúng email vào message — tránh leak qua error body / logs client-facing
            throw new AuthException(AuthException.Kind.CONFLICT, "Email already registered");
        }
        String hash = passwordHasher.hash(password);
        User user = new User(normalizedEmail, hash, displayName);
        user.setRole(UserRole.USER);
        user.setAuthProvider(AuthProvider.LOCAL);
        return userRepository.save(user);
    }
}