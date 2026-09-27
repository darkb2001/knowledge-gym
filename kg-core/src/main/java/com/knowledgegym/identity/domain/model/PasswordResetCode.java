package com.knowledgegym.identity.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Password reset code — thuần Java domain entity.
 * Lifecycle: tạo khi POST /auth/forgot-password → verify khi POST /auth/reset-password.
 * Bảo mật: SHA-256(code) lưu trong DB; single-use qua usedAt; max 5 attempts.
 */
public class PasswordResetCode {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration TTL = Duration.ofMinutes(10);

    private UUID id;
    private UUID userId;
    private String codeHash;
    private int attempts;
    private Instant usedAt;
    private Instant expiresAt;
    private Instant createdAt;

    public PasswordResetCode() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.expiresAt = Instant.now().plus(TTL);
    }

    public PasswordResetCode(UUID userId, String codeHash) {
        this();
        this.userId = userId;
        this.codeHash = codeHash;
        this.attempts = 0;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isAttemptsExhausted() {
        return attempts >= MAX_ATTEMPTS;
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}