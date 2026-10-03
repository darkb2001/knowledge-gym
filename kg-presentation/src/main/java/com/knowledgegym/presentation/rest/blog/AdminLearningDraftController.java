package com.knowledgegym.presentation.rest.blog;

import com.knowledgegym.blog.application.GenerateLearningDraftUseCase;
import com.knowledgegym.blog.domain.port.LearningContentDraftPort;
import com.knowledgegym.presentation.rest.content.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/knowledge/drafts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminLearningDraftController {
    private final GenerateLearningDraftUseCase drafts;
    public AdminLearningDraftController(GenerateLearningDraftUseCase drafts){this.drafts=drafts;}
    public record GenerateInput(@NotNull LearningContentDraftPort.Kind kind,@NotBlank @Size(max=200) String topic,
                                @NotBlank @Size(max=5000) String instruction,@Size(max=20) @NotEmpty List<UUID> sourceIds,UUID goalId){}
    public record ReviewInput(@NotNull LearningContentDraftPort.Status status,@NotBlank @Size(max=500) String reason){}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public LearningContentDraftPort.Draft generate(@AuthenticationPrincipal UUID actor,@Valid @RequestBody GenerateInput i){return drafts.generate(actor,i.goalId(),i.kind(),i.topic(),i.instruction(),i.sourceIds());}
    @GetMapping public PageResponse<LearningContentDraftPort.Draft> list(@RequestParam(required=false) LearningContentDraftPort.Status status,@RequestParam(required=false) LearningContentDraftPort.Kind kind,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return PageResponse.of(drafts.list(status,kind,page,size),v->v);}
    @GetMapping("/{id}") public LearningContentDraftPort.Draft get(@PathVariable UUID id){return drafts.get(id);}
    @PatchMapping("/{id}/review") public LearningContentDraftPort.Draft review(@AuthenticationPrincipal UUID actor,@PathVariable UUID id,@Valid @RequestBody ReviewInput i){return drafts.review(actor,id,i.status(),i.reason());}
    public record RollbackInput(@NotBlank @Size(max=500) String reason){}
    @PostMapping("/{id}/rollback")
    @org.springframework.cache.annotation.CacheEvict(value={"questions","topics","modules"},allEntries=true)
    public java.util.Map<String,UUID> rollback(@AuthenticationPrincipal UUID actor,@PathVariable UUID id,@Valid @RequestBody RollbackInput i){return java.util.Map.of("questionId",drafts.rollback(actor,id,i.reason()));}
    public record MaterializeInput(@NotNull UUID moduleId,@NotBlank @Size(max=500) String reason){}
    @PostMapping("/{id}/materialize-question") @ResponseStatus(HttpStatus.CREATED)
    public java.util.Map<String,UUID> materializeQuestion(@AuthenticationPrincipal UUID actor,@PathVariable UUID id,@Valid @RequestBody MaterializeInput i){return java.util.Map.of("questionId",drafts.materializeQuestion(actor,id,i.moduleId(),i.reason()));}
    @PostMapping("/{id}/materialize") @ResponseStatus(HttpStatus.CREATED)
    public java.util.Map<String,UUID> materialize(@AuthenticationPrincipal UUID actor,@PathVariable UUID id,@Valid @RequestBody MaterializeInput i){var draft=drafts.get(id);return java.util.Map.of("questionId",drafts.materialize(actor,id,i.moduleId(),i.reason(),draft.kind()));}
}
