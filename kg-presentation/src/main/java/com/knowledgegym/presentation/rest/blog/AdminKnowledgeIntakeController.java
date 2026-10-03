package com.knowledgegym.presentation.rest.blog;

import com.knowledgegym.blog.application.ManageKnowledgeIntakeUseCase;
import com.knowledgegym.blog.domain.port.KnowledgeIntakePort;
import com.knowledgegym.presentation.rest.content.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/knowledge")
@PreAuthorize("hasRole('ADMIN')")
public class AdminKnowledgeIntakeController {
    private final ManageKnowledgeIntakeUseCase intake;
    public AdminKnowledgeIntakeController(ManageKnowledgeIntakeUseCase intake){this.intake=intake;}
    public record GoalInput(@NotBlank @Size(max=200) String name,@NotBlank @Size(max=200) String topic,
            @NotBlank @Size(max=10000) String objective,boolean autoPublish,@Min(1) @Max(200) int dailyItemLimit,
            @NotNull @DecimalMin("0") @DecimalMax("10000") BigDecimal dailyCostLimitUsd,
            @Size(max=100) String scheduleCron,@NotEmpty List<@NotBlank String> allowedDomains){}
    public record StatusInput(@NotNull KnowledgeIntakePort.GoalStatus status,@NotBlank @Size(max=500) String reason){}
    public record RunInput(@NotBlank @Size(max=500) String reason){}
    @GetMapping("/goals") public List<KnowledgeIntakePort.Goal> goals(@RequestParam(required=false) KnowledgeIntakePort.GoalStatus status){return intake.goals(status);}
    @GetMapping("/goals/{id}") public KnowledgeIntakePort.Goal goal(@PathVariable UUID id){return intake.goal(id);}
    @PostMapping("/goals") @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeIntakePort.Goal create(@AuthenticationPrincipal UUID actor,@Valid @RequestBody GoalInput i){return intake.create(actor,i.name(),i.topic(),i.objective(),i.autoPublish(),i.dailyItemLimit(),i.dailyCostLimitUsd(),i.scheduleCron(),i.allowedDomains());}
    @PatchMapping("/goals/{id}/status") public KnowledgeIntakePort.Goal status(@AuthenticationPrincipal UUID actor,@PathVariable UUID id,@Valid @RequestBody StatusInput i){return intake.status(actor,id,i.status(),i.reason());}
    @PostMapping("/goals/{id}/runs") @ResponseStatus(HttpStatus.ACCEPTED)
    public KnowledgeIntakePort.Run run(@AuthenticationPrincipal UUID actor,@PathVariable UUID id,@Valid @RequestBody RunInput i){return intake.run(actor,id,i.reason());}
    @GetMapping("/goals/{id}/runs") public PageResponse<KnowledgeIntakePort.Run> runs(@PathVariable UUID id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return PageResponse.of(intake.runs(id,page,size),v->v);}
}
