package com.knowledgegym.identity.domain.port;

import com.knowledgegym.shared.domain.model.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Administrative projections never expose credentials or OAuth identifiers. */
public interface AdminUserPort {
    record UserView(UUID id, String email, String displayName, UserRole role,
                    boolean blocked, boolean emailVerified, String authProvider,
                    int xp, Instant createdAt, Instant updatedAt) {}
    record UserPage(List<UserView> items, int page, int size, long totalElements, int totalPages) {}
    UserPage list(String query, UserRole role, Boolean blocked, int page, int size);
    UserView get(UUID id);
    /** Serializes security mutations, including the last-admin check. */
    void lockAdministration();
    long activeAdminCount();
    void changeRole(UUID id, UserRole role);
    void setBlocked(UUID id, boolean blocked);
    void revokeSessions(UUID id);
    void audit(UUID actor, UUID target, String action, String reason);
}
