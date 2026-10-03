package com.knowledgegym.presentation.rest.blog;

import com.knowledgegym.blog.application.ModerateBlogUseCase;
import com.knowledgegym.blog.domain.port.AdminModerationPort;
import com.knowledgegym.presentation.rest.content.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/admin/blog")
@PreAuthorize("hasRole('ADMIN')")
public class AdminModerationController {
    private final ModerateBlogUseCase moderation;
    public AdminModerationController(ModerateBlogUseCase moderation) { this.moderation = moderation; }
    public record Reason(@NotBlank @Size(max=500) String reason) {}
    public record CommentInput(@NotNull AdminModerationPort.CommentStatus status, @NotBlank @Size(max=500) String reason) {}
    public record PostInput(@NotNull AdminModerationPort.PostAction action, @NotBlank @Size(max=500) String reason) {}
    public record PostResult(UUID id, String status) {}
    @GetMapping("/comments")
    public PageResponse<AdminModerationPort.CommentView> comments(@RequestParam(required=false) String q,
            @RequestParam(required=false) UUID postId, @RequestParam(required=false) UUID userId,
            @RequestParam(required=false) AdminModerationPort.CommentStatus status,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return PageResponse.of(moderation.comments(q,postId,userId,status,page,size), value->value);
    }
    @PatchMapping("/comments/{id}/status")
    public AdminModerationPort.CommentView comment(@AuthenticationPrincipal UUID actor, @PathVariable UUID id,
                                                   @Valid @RequestBody CommentInput input) {
        return moderation.comment(actor,id,input.status(),false,input.reason());
    }
    @DeleteMapping("/comments/{id}")
    public AdminModerationPort.CommentView deleteComment(@AuthenticationPrincipal UUID actor, @PathVariable UUID id,
                                                         @Valid @RequestBody Reason input) {
        return moderation.comment(actor,id,AdminModerationPort.CommentStatus.DELETED,false,input.reason());
    }
    @PostMapping("/comments/{id}/restore")
    public AdminModerationPort.CommentView restoreComment(@AuthenticationPrincipal UUID actor, @PathVariable UUID id,
                                                          @Valid @RequestBody Reason input) {
        return moderation.comment(actor,id,AdminModerationPort.CommentStatus.HIDDEN,true,input.reason());
    }
    @PatchMapping("/posts/{id}/status")
    public PostResult post(@AuthenticationPrincipal UUID actor, @PathVariable UUID id,
                           @Valid @RequestBody PostInput input) {
        return new PostResult(id,moderation.post(actor,id,input.action(),input.reason()));
    }
    @DeleteMapping("/posts/{id}")
    public PostResult deletePost(@AuthenticationPrincipal UUID actor, @PathVariable UUID id,
                                 @Valid @RequestBody Reason input) {
        return new PostResult(id,moderation.post(actor,id,AdminModerationPort.PostAction.DELETE,input.reason()));
    }
    @PostMapping("/posts/{id}/restore")
    public PostResult restorePost(@AuthenticationPrincipal UUID actor, @PathVariable UUID id,
                                  @Valid @RequestBody Reason input) {
        return new PostResult(id,moderation.post(actor,id,AdminModerationPort.PostAction.RESTORE,input.reason()));
    }
}
