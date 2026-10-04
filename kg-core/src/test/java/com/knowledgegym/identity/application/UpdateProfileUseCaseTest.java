package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UpdateProfileUseCaseTest {
    @Test
    void malformedOrCredentialBearingUrlsAreClientErrorsAndAreNotSaved() {
        var users = mock(UserRepository.class);
        var user = new User(UUID.randomUUID());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        var useCase = new UpdateProfileUseCase(users, Optional.empty());
        for (String url : new String[]{"https:/avatar.png", "http:avatar.png", "https://user:pass@example.com/avatar.png", "javascript:alert(1)"}) {
            assertThatThrownBy(() -> useCase.execute(user.getId(), null, url))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(users, never()).save(any());
    }

    @Test
    void validStoredAvatarCanBeSetAndCleared() {
        var users = mock(UserRepository.class);
        var user = new User(UUID.randomUUID());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var useCase = new UpdateProfileUseCase(users, Optional.of("https://storage.example.com/avatars/"));
        assertThat(useCase.execute(user.getId(), null, "https://storage.example.com/avatars/me.png").getAvatarUrl())
                .isEqualTo("https://storage.example.com/avatars/me.png");
        assertThat(useCase.execute(user.getId(), null, " ").getAvatarUrl()).isNull();
        assertThatThrownBy(() -> useCase.execute(user.getId(), null, "https://other.example.com/me.png"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
