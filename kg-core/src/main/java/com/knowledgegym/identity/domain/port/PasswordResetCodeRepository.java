package com.knowledgegym.identity.domain.port;

import com.knowledgegym.identity.domain.model.PasswordResetCode;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetCodeRepository {
    PasswordResetCode save(PasswordResetCode code);
    /** Code mới nhất (chưa dùng / chưa hết hạn) của user. */
    Optional<PasswordResetCode> findLatestActiveByUserId(UUID userId);
}