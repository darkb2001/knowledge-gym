package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.port.AdminUserPort;
import com.knowledgegym.shared.application.ConflictException;
import com.knowledgegym.shared.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManageUsersUseCaseTest {
    private final UUID actor = UUID.randomUUID(), target = UUID.randomUUID();
    private AdminUserPort port;
    private ManageUsersUseCase users;
    @BeforeEach void setup() {
        port = mock(AdminUserPort.class);
        users = new ManageUsersUseCase(port);
        when(port.get(actor)).thenReturn(view(actor, UserRole.ADMIN, false));
        when(port.get(target)).thenReturn(view(target, UserRole.USER, false));
    }
    @Test void roleChangeRevokesSessionsAndAuditsInOrder() {
        users.changeRole(actor, target, UserRole.PREMIUM, "Subscription support");
        var order = inOrder(port);
        order.verify(port).lockAdministration();
        order.verify(port).get(actor);
        order.verify(port).get(target);
        order.verify(port).changeRole(target, UserRole.PREMIUM);
        order.verify(port).revokeSessions(target);
        order.verify(port).audit(actor, target, "ADMIN_USER_ROLE_PREMIUM", "Subscription support");
    }
    @Test void cannotDemoteLastActiveAdmin() {
        when(port.get(target)).thenReturn(view(target, UserRole.ADMIN, false));
        when(port.activeAdminCount()).thenReturn(1L);
        assertThrows(ConflictException.class, () -> users.changeRole(actor, target, UserRole.USER, "Policy"));
        verify(port, never()).changeRole(any(), any());
    }
    @Test void cannotBlockLastActiveAdmin() {
        when(port.get(target)).thenReturn(view(target, UserRole.ADMIN, false));
        when(port.activeAdminCount()).thenReturn(1L);
        assertThrows(ConflictException.class, () -> users.setBlocked(actor, target, true, "Policy"));
        verify(port, never()).setBlocked(any(), anyBoolean());
    }
    @Test void cannotLockOrDemoteSelf() {
        assertThrows(ConflictException.class, () -> users.setBlocked(actor, actor, true, "Policy"));
        assertThrows(ConflictException.class, () -> users.changeRole(actor, actor, UserRole.USER, "Policy"));
    }
    @Test void blockAndUnblockBothInvalidateOldSessions() {
        users.setBlocked(actor, target, true, "Abuse");
        verify(port).setBlocked(target, true);
        verify(port).revokeSessions(target);
        when(port.get(target)).thenReturn(view(target, UserRole.USER, true));
        users.setBlocked(actor, target, false, "Appeal accepted");
        verify(port).setBlocked(target, false);
        verify(port, times(2)).revokeSessions(target);
    }
    @Test void staleOrBlockedActorCannotMutate() {
        when(port.get(actor)).thenReturn(view(actor, UserRole.USER, false));
        assertThrows(AuthException.class, () -> users.revokeSessions(actor, target, "Policy"));
        when(port.get(actor)).thenReturn(view(actor, UserRole.ADMIN, true));
        assertThrows(AuthException.class, () -> users.revokeSessions(actor, target, "Policy"));
        verify(port, never()).revokeSessions(any());
    }
    @Test void reasonAndPaginationAreValidatedBeforeWrites() {
        assertThrows(IllegalArgumentException.class, () -> users.setBlocked(actor, target, true, " "));
        assertThrows(IllegalArgumentException.class, () -> users.changeRole(actor, target, null, "Policy"));
        assertThrows(IllegalArgumentException.class, () -> users.list(null, null, null, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> users.list(null, null, null, 1, 101));
        verifyNoInteractions(port);
    }
    @Test void unchangedRoleIsIdempotent() {
        users.changeRole(actor, target, UserRole.USER, "Policy");
        verify(port, never()).revokeSessions(any());
        verify(port, never()).audit(any(), any(), any(), any());
    }
    private static AdminUserPort.UserView view(UUID id, UserRole role, boolean blocked) {
        return new AdminUserPort.UserView(id, "test@example.com", "Test", role, blocked,
                true, "LOCAL", 0, Instant.EPOCH, Instant.EPOCH);
    }
}
