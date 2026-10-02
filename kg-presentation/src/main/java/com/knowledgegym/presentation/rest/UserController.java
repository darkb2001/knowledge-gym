package com.knowledgegym.presentation.rest;

import com.knowledgegym.identity.application.GetCurrentUserUseCase;
import com.knowledgegym.identity.application.UpdateProfileUseCase;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.presentation.rest.dashboard.dto.DashboardResponses;
import com.knowledgegym.progress.application.QueryProgressUseCase;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {
    private final GetCurrentUserUseCase currentUser;
    private final UpdateProfileUseCase updateProfile;
    private final QueryProgressUseCase progress;

    public UserController(GetCurrentUserUseCase currentUser,
                          UpdateProfileUseCase updateProfile,
                          QueryProgressUseCase progress) {
        this.currentUser = currentUser;
        this.updateProfile = updateProfile;
        this.progress = progress;
    }

    @GetMapping("/users/me")
    public ProfileResponse me(@AuthenticationPrincipal UUID userId) {
        return ProfileResponse.of(currentUser.execute(userId), progress.stats(userId));
    }

    @PatchMapping("/users/me")
    public ProfileResponse update(@AuthenticationPrincipal UUID userId,
                                  @RequestBody UpdateProfileRequest request) {
        User updated = updateProfile.execute(userId, request.displayName(), request.avatarUrl());
        return ProfileResponse.of(updated, progress.stats(userId));
    }

    public record UpdateProfileRequest(String displayName, String avatarUrl) {}

    public record ProfileResponse(UUID id, String email, String displayName, String avatarUrl,
                                  String role, String authProvider, DashboardResponses.Stats stats) {
        static ProfileResponse of(User user, QueryProgressUseCase.Stats stats) {
            return new ProfileResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                    user.getAvatarUrl(), user.getRole().name(), user.getAuthProvider().name(),
                    DashboardResponses.Stats.of(stats));
        }
    }
}
