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
     * @throws IllegalArgumentException email trùng.
     * @throws IllegalArgumentException password < 8 ký tự.
     */
    public User execute(String email, String password, String displayName) {
        String normalizedEmail = User.normalizeEmail(email);
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email already registered: " + normalizedEmail);
        }
        String hash = passwordHasher.hash(password);
        User user = new User(normalizedEmail, hash, displayName);
        user.setRole(UserRole.USER);
        user.setAuthProvider(AuthProvider.LOCAL);
        return userRepository.save(user);
    }
}