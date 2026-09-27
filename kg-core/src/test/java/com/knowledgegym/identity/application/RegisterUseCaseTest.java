package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.PasswordHasher;
import com.knowledgegym.identity.domain.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class RegisterUseCaseTest {

    private UserRepository userRepository;
    private RegisterUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        useCase = new RegisterUseCase(userRepository, new FakePasswordHasher());
    }

    @Test
    void register_newEmail_persistsUser() {
        User user = useCase.execute("new@example.com", "password123", "New User");

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getDisplayName()).isEqualTo("New User");
        assertThat(user.getPasswordHash()).startsWith("$2a$"); // BCrypt prefix
        assertThat(userRepository.existsByEmail("new@example.com")).isTrue();
    }

    @Test
    void register_emailIsNormalized_toLowercase() {
        User user = useCase.execute("  USER@Example.COM  ", "password123", "U");
        assertThat(user.getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void register_duplicateEmail_throws() {
        useCase.execute("dup@example.com", "password123", "A");
        assertThatThrownBy(() -> useCase.execute("dup@example.com", "password123", "B"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void register_shortPassword_throws() {
        assertThatThrownBy(() -> useCase.execute("x@example.com", "short", "X"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("at least 8");
    }

    @Test
    void register_emailIsCaseInsensitive_forUniqueness() {
        useCase.execute("case@example.com", "password123", "A");
        assertThatThrownBy(() -> useCase.execute("CASE@example.com", "password123", "B"))
                .isInstanceOf(AuthException.class);
    }

    // --- Test doubles ---

    static class FakePasswordHasher implements PasswordHasher {
        @Override public String hash(String rawPassword) { return "$2a$12$fakefakefakefakefakefakefakefakefakefakefakefakefake"; }
        @Override public boolean matches(String rawPassword, String hashedPassword) { return hashedPassword.equals(hash(rawPassword)); }
    }

    static class InMemoryUserRepository implements UserRepository {
        private final java.util.Map<UUID, User> store = new java.util.HashMap<>();

        @Override public Optional<User> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public Optional<User> findByEmail(String email) {
            String norm = User.normalizeEmail(email);
            return store.values().stream().filter(u -> u.getEmail().equals(norm)).findFirst();
        }
        @Override public boolean existsByEmail(String email) { return findByEmail(email).isPresent(); }
        @Override public User save(User user) { store.put(user.getId(), user); return user; }
        @Override public void deleteById(UUID id) { store.remove(id); }
    }
}