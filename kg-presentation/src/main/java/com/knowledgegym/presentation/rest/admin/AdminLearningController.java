package com.knowledgegym.presentation.rest.admin;

import com.knowledgegym.learning.application.AdminLearningUseCase;
import com.knowledgegym.learning.domain.port.AdminLearningPort;
import com.knowledgegym.presentation.rest.content.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/admin/users/{userId}/learning")
@PreAuthorize("hasRole('ADMIN')")
public class AdminLearningController {
    private final AdminLearningUseCase learning;
    public AdminLearningController(AdminLearningUseCase learning) { this.learning = learning; }
    public record Reason(@NotBlank @Size(max=500) String reason) {}
    @GetMapping
    public PageResponse<AdminLearningPort.Entry> list(@PathVariable UUID userId,
            @RequestParam AdminLearningPort.Kind kind, @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int size) {
        return PageResponse.of(learning.list(userId,kind,page,size),v->v);
    }
    @PostMapping("/srs/{cardId}/reset") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@AuthenticationPrincipal UUID actor, @PathVariable UUID userId,
                      @PathVariable UUID cardId, @Valid @RequestBody Reason reason) {
        learning.resetCard(actor,userId,cardId,reason.reason());
    }
}
