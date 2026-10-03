package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.port.AdminUserPort;
import com.knowledgegym.identity.domain.port.AdminUserPort.UserView;
import com.knowledgegym.shared.application.ConflictException;
import com.knowledgegym.shared.domain.model.UserRole;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

public class ManageUsersUseCase {
    private final AdminUserPort users;
    public ManageUsersUseCase(AdminUserPort users) { this.users = users; }

    public AdminUserPort.UserPage list(String q, UserRole role, Boolean blocked, int page, int size) {
        if (page < 1 || size < 1 || size > 100 || q != null && q.length() > 200)
            throw new IllegalArgumentException("page >= 1, size 1..100, q tối đa 200 ký tự");
        return users.list(q, role, blocked, page, size);
    }
    public UserView get(UUID id) { return users.get(id); }

    @Transactional
    public UserView changeRole(UUID actor, UUID id, UserRole role, String reason) {
        if (role == null) throw new IllegalArgumentException("role bắt buộc");
        validateReason(reason);
        authorize(actor);
        var current = users.get(id);
        if (current.role() == role) return current;
        if (actor.equals(id)) throw new ConflictException("Không tự thay đổi quyền của mình");
        protectLastAdmin(current, role != UserRole.ADMIN);
        users.changeRole(id, role);
        users.revokeSessions(id);
        users.audit(actor, id, "ADMIN_USER_ROLE_" + role.name(), reason.trim());
        return users.get(id);
    }

    @Transactional
    public UserView setBlocked(UUID actor, UUID id, boolean blocked, String reason) {
        validateReason(reason);
        authorize(actor);
        var current = users.get(id);
        if (current.blocked() == blocked) return current;
        if (actor.equals(id)) throw new ConflictException("Không tự khóa tài khoản của mình");
        protectLastAdmin(current, blocked);
        users.setBlocked(id, blocked);
        users.revokeSessions(id);
        users.audit(actor, id, blocked ? "ADMIN_USER_BLOCK" : "ADMIN_USER_UNBLOCK", reason.trim());
        return users.get(id);
    }

    @Transactional
    public void revokeSessions(UUID actor, UUID id, String reason) {
        validateReason(reason);
        authorize(actor);
        users.get(id);
        users.revokeSessions(id);
        users.audit(actor, id, "ADMIN_USER_REVOKE_SESSIONS", reason.trim());
    }

    private void authorize(UUID actor) {
        users.lockAdministration();
        var admin = users.get(actor);
        if (admin.blocked() || admin.role() != UserRole.ADMIN)
            throw new AuthException("Admin session no longer authorized");
    }
    private void protectLastAdmin(UserView current, boolean removesAdmin) {
        if (removesAdmin && !current.blocked() && current.role() == UserRole.ADMIN
                && users.activeAdminCount() <= 1)
            throw new ConflictException("Không thể khóa hoặc hạ quyền admin cuối cùng");
    }
    private static void validateReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new IllegalArgumentException("reason bắt buộc, tối đa 500 ký tự");
    }
}
