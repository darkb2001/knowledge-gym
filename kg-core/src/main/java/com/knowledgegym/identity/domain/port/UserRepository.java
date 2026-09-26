package com.knowledgegym.identity.domain.port;

import com.knowledgegym.identity.domain.model.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findById(UUID id);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    User save(User user);
    void deleteById(UUID id);
}
