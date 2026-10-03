package com.knowledgegym.presentation.rest.admin;

import com.knowledgegym.identity.application.ManageUsersUseCase;
import com.knowledgegym.identity.domain.port.AdminUserPort;
import com.knowledgegym.shared.domain.model.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUsersController {
    private final ManageUsersUseCase users;
    public AdminUsersController(ManageUsersUseCase users) { this.users = users; }
    public record RoleInput(@NotNull UserRole role, @NotBlank @Size(max=500) String reason) {}
    public record StatusInput(@NotNull Boolean blocked, @NotBlank @Size(max=500) String reason) {}
    public record RevokeInput(@NotBlank @Size(max=500) String reason) {}

    @GetMapping
    public AdminUserPort.UserPage list(@RequestParam(required=false) String q,
            @RequestParam(required=false) UserRole role, @RequestParam(required=false) Boolean blocked,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return users.list(q, role, blocked, page, size);
    }
    @GetMapping("/{id}")
    public AdminUserPort.UserView detail(@PathVariable UUID id) { return users.get(id); }
    @PatchMapping("/{id}/role")
    public AdminUserPort.UserView role(@AuthenticationPrincipal UUID actor, @PathVariable UUID id,
                                       @Valid @RequestBody RoleInput input) {
        return users.changeRole(actor, id, input.role(), input.reason());
    }
    @PatchMapping("/{id}/status")
    public AdminUserPort.UserView status(@AuthenticationPrincipal UUID actor, @PathVariable UUID id,
                                         @Valid @RequestBody StatusInput input) {
        return users.setBlocked(actor, id, input.blocked(), input.reason());
    }
    @PostMapping("/{id}/revoke-sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal UUID actor, @PathVariable UUID id,
                       @Valid @RequestBody RevokeInput input) {
        users.revokeSessions(actor, id, input.reason());
    }
}
