package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.UserRepository;
import java.util.UUID;

public final class GetCurrentUserUseCase {
    private final UserRepository users;

    public GetCurrentUserUseCase(UserRepository users) {
        this.users = users;
    }

    public User execute(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
