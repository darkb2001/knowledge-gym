package com.knowledgegym.identity.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token domain record — bản audit trong PostgreSQL (bảng refresh_tokens).
 * Redis giữ lookup O(1); PG sống sót qua Redis restart + family reuse detection.
 */
public class RefreshToken {

    public static final Duration TTL = Duration.ofDays(7);

    private UUID id;
    private UUID userId;
    private String tokenHash;
    private UUID familyId;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant revokedAt;
    private UUID replacedBy;
    private String ipAddress;
    private String userAgent;

    public RefreshToken() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.expiresAt = Instant.now().plus(TTL);
    }

    public RefreshToken(UUID userId, String tokenHash, UUID familyId) {
        this();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
    }

    public void revoke(UUID replacedBy) {
        this.revokedAt = Instant.now();
        this.replacedBy = replacedBy;
    }

    public boolean isRevoked() { return revokedAt != null; }
    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public UUID getFamilyId() { return familyId; }
    public void setFamilyId(UUID familyId) { this.familyId = familyId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public UUID getReplacedBy() { return replacedBy; }
    public void setReplacedBy(UUID replacedBy) { this.replacedBy = replacedBy; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}