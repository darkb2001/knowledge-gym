package com.knowledgegym.presentation.rest.blog;

import com.knowledgegym.blog.application.BlogPostsUseCase;
import com.knowledgegym.blog.application.CollectItemsUseCase;
import com.knowledgegym.blog.domain.model.BlogPost;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/blog")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBlogController {
    private final BlogPostsUseCase posts;
    private final CollectItemsUseCase collector;
    public AdminBlogController(BlogPostsUseCase posts,CollectItemsUseCase collector){this.posts=posts;this.collector=collector;}
    public record DraftRequest(@NotBlank String title,@NotBlank String body,UUID moduleId,UUID questionId,List<String> tags){}
    public record DraftResponse(UUID id,String title,String slug,String status){static DraftResponse of(BlogPost p){return new DraftResponse(p.id(),p.title(),p.slug(),p.status());}}
    @PostMapping("/collect") public CollectItemsUseCase.Result collect(){return collector.execute();}
    @PostMapping("/posts") @ResponseStatus(HttpStatus.CREATED)
    public DraftResponse draft(@AuthenticationPrincipal UUID userId,@Valid @RequestBody DraftRequest request){return DraftResponse.of(posts.createDraft(userId,request.title(),request.body(),request.moduleId(),request.questionId(),request.tags()));}
    @PostMapping("/posts/{id}/publish") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void publish(@PathVariable UUID id){posts.publish(id);}
}
