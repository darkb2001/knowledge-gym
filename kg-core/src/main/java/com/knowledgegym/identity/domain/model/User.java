package com.knowledgegym.identity.domain.model;

import com.knowledgegym.shared.domain.model.BaseEntity;
import com.knowledgegym.shared.domain.model.UserRole;

import java.util.Objects;
import java.util.UUID;

/**
 * User Aggregate Root — thuần Java domain entity.
 * Không JPA annotation, không Spring — mapping ở infrastructure layer (UserJpaEntity).
 *
 * Invariant: setter mutate field auditable đều gọi touch() để updatedAt phản ánh
 * đúng thay đổi (review H2). Domain methods (changePassword...) validate chặt hơn.
 */
public class User extends BaseEntity {

    private String email;
    private String passwordHash;
    private String displayName;
    private String avatarUrl;
    private UserRole role;
    private AuthProvider authProvider;
    private String oauthId;

    public User() {
        super();
        this.role = UserRole.USER;
        this.authProvider = AuthProvider.LOCAL;
    }

    public User(String email, String passwordHash, String displayName) {
        this();
        this.email = normalizeEmail(email);
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.displayName = displayName;
    }

    /**
     * Factory OAuth — passwordHash null hợp lệ (DB nullable, login LOCAL sẽ reject).
     */
    public static User createGoogleUser(String email, String displayName, String googleSub) {
        User u = new User();
        u.email = normalizeEmail(email);
        u.passwordHash = null;
        u.displayName = displayName != null ? displayName : email;
        u.authProvider = AuthProvider.GOOGLE;
        u.oauthId = Objects.requireNonNull(googleSub, "googleSub");
        return u;
    }

    public User(UUID id) {
        super(id);
    }

    /**
     * Domain logic: email chuẩn hóa để tránh duplicate (đây là lý do equals/hashCode
     * phải考虑 business identity, không chỉ id — Module 01 lesson).
     */
    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email").trim().toLowerCase();
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public void promoteToPremium() {
        if (role == UserRole.ADMIN) {
            throw new IllegalStateException("Admin cannot be demoted to premium");
        }
        this.role = UserRole.PREMIUM;
        touch();
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash);
        touch();
    }

    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = normalizeEmail(email);
        touch();
    }
    public String getPasswordHash() { return passwordHash; }
    /** Cho phép null — OAuth-only user không có mật khẩu LOCAL. */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        touch();
    }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        touch();
    }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
        touch();
    }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) {
        this.role = Objects.requireNonNull(role, "role");
        touch();
    }
    public AuthProvider getAuthProvider() { return authProvider; }
    public void setAuthProvider(AuthProvider authProvider) {
        this.authProvider = Objects.requireNonNull(authProvider, "authProvider");
        touch();
    }
    public String getOauthId() { return oauthId; }
    public void setOauthId(String oauthId) { this.oauthId = oauthId; }
}
