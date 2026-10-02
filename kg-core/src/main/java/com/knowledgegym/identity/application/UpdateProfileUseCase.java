package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.UserRepository;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class UpdateProfileUseCase {
    private static final int DISPLAY_NAME_MAX = 80;
    private final UserRepository users;
    private final Optional<String> avatarUrlPrefix;

    public UpdateProfileUseCase(UserRepository users, Optional<String> avatarUrlPrefix) {
        this.users = users;
        this.avatarUrlPrefix = avatarUrlPrefix;
    }

    public User execute(UUID userId, String displayName, String avatarUrl) {
        User user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (displayName != null) {
            String trimmed = displayName.trim();
            if (trimmed.isEmpty() || trimmed.length() > DISPLAY_NAME_MAX) {
                throw new IllegalArgumentException("displayName must be 1-" + DISPLAY_NAME_MAX + " characters");
            }
            user.setDisplayName(trimmed);
        }
        if (avatarUrl != null) {
            String trimmed = avatarUrl.trim();
            if (trimmed.isEmpty()) {
                user.setAvatarUrl(null);
            } else {
                validateAvatarUrl(trimmed);
                user.setAvatarUrl(trimmed);
            }
        }
        return users.save(user);
    }

    private void validateAvatarUrl(String avatarUrl) {
        URI uri;
        try {
            uri = URI.create(avatarUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("avatarUrl must be an absolute http(s) URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("avatarUrl must use http or https");
        }
        avatarUrlPrefix.ifPresent(prefix -> {
            if (!avatarUrl.startsWith(prefix)) {
                throw new IllegalArgumentException("avatarUrl must be hosted on the configured object storage");
            }
        });
        Objects.requireNonNull(uri.getHost(), "avatarUrl must include a host");
    }
}
