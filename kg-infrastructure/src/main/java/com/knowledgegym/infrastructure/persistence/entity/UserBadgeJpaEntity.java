package com.knowledgegym.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite PK (user_id, badge_code) — map từ V008.
 * EmbeddedId dùng để JPA hiểu primary key tổng hợp.
 */
@Entity
@Table(name = "user_badges")
public class UserBadgeJpaEntity {

    @EmbeddedId
    private UserBadgeId id;

    @Column(name = "earned_at", nullable = false, updatable = false)
    private Instant earnedAt;

    public UserBadgeJpaEntity() {}

    public UserBadgeJpaEntity(UUID userId, String badgeCode) {
        this.id = new UserBadgeId(userId, badgeCode);
    }

    public UserBadgeId getId() { return id; }
    public void setId(UserBadgeId id) { this.id = id; }
    public Instant getEarnedAt() { return earnedAt; }
    public void setEarnedAt(Instant earnedAt) { this.earnedAt = earnedAt; }

    @Embeddable
    public static class UserBadgeId implements Serializable {

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        @Column(name = "badge_code", nullable = false, length = 50)
        private String badgeCode;

        public UserBadgeId() {}

        public UserBadgeId(UUID userId, String badgeCode) {
            this.userId = userId;
            this.badgeCode = badgeCode;
        }

        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
        public String getBadgeCode() { return badgeCode; }
        public void setBadgeCode(String badgeCode) { this.badgeCode = badgeCode; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserBadgeId that = (UserBadgeId) o;
            return Objects.equals(userId, that.userId) && Objects.equals(badgeCode, that.badgeCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, badgeCode);
        }
    }
}