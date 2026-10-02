package com.knowledgegym.presentation.rest.blog;

import com.knowledgegym.blog.application.BlogPostsUseCase;
import com.knowledgegym.blog.application.BlogWriterAdminUseCase;
import com.knowledgegym.blog.application.ReviseBlogDraftUseCase;
import com.knowledgegym.blog.domain.model.BlogPost;
import com.knowledgegym.blog.domain.port.BlogScheduleSettingsPort;
import com.knowledgegym.blog.domain.port.BlogWriterRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/blog/writer")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBlogWriterController {
    private final BlogWriterAdminUseCase admin;private final ReviseBlogDraftUseCase revise;
    private final BlogPostsUseCase posts;private final BlogPostsUseCase.HtmlSanitizer sanitizer;
    private final boolean apiKeyConfigured;
    private final boolean writerEnabled;
    private final double monthlyCostAlertUsd;
    public AdminBlogWriterController(BlogWriterAdminUseCase admin,ReviseBlogDraftUseCase revise,BlogPostsUseCase posts,
            BlogPostsUseCase.HtmlSanitizer sanitizer,@Value("${app.blog.writer.api-key:}") String apiKey,
            @Value("${app.blog.writer.enabled:false}") boolean writerEnabled,
            @Value("${app.blog.writer.monthly-cost-alert-usd:10}") double monthlyCostAlertUsd){
        this.admin=admin;this.revise=revise;this.posts=posts;this.sanitizer=sanitizer;
        this.apiKeyConfigured=apiKey!=null&&!apiKey.isBlank();
        this.writerEnabled=writerEnabled;
        this.monthlyCostAlertUsd=monthlyCostAlertUsd;
    }
    public record SettingsResponse(BlogScheduleSettingsPort.Settings settings,boolean apiKeyConfigured,boolean writerEnabled,java.time.Instant nextRunAt){}
    public record SettingsRequest(boolean enabled,@NotBlank String localTime,@NotBlank String timezone,int dailyLimit,
            @NotBlank String publishPolicy,int qualityThreshold){}
    public record GenerateRequest(String topic,String requestId){}
    public record RunResponse(UUID id,String status,int attempts,String errorMessage){}
    public record ReviseRequest(@NotBlank String instruction){}
    public record ManualEditRequest(@NotBlank String title,@NotBlank String body,String excerpt){}
    public record DraftActionResponse(UUID id,String status){}
    public record StatsResponse(BlogWriterRepository.Stats stats,double monthlyCostAlertUsd){}

    @GetMapping("/settings") public SettingsResponse settings(){return settingsResponse();}
    @PutMapping("/settings") public SettingsResponse update(@Valid @RequestBody SettingsRequest request,@AuthenticationPrincipal UUID actor){
        var policy=BlogScheduleSettingsPort.PublishPolicy.valueOf(request.publishPolicy());
        admin.update(request.enabled(),request.localTime(),request.timezone(),request.dailyLimit(),policy,request.qualityThreshold(),actor);return settingsResponse();
    }
    @PostMapping("/runs") @ResponseStatus(HttpStatus.ACCEPTED)
    public RunResponse generateNow(@RequestBody(required=false) GenerateRequest request,@AuthenticationPrincipal UUID actor){
        if(!apiKeyConfigured)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI writer is not configured");
        if(!writerEnabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI writer worker is disabled");
        UUID id=admin.generateNow(request==null?null:request.topic(),actor,request==null?null:request.requestId());return new RunResponse(id,"QUEUED",0,null);
    }
    @GetMapping("/runs/{id}") public RunResponse run(@PathVariable UUID id){var job=admin.job(id);return new RunResponse(job.id(),job.status(),job.attempts(),job.errorMessage());}
    @GetMapping("/review") public List<BlogPost> reviewQueue(){return admin.reviewQueue();}
    @GetMapping("/stats") public StatsResponse stats(){return new StatsResponse(admin.stats(),monthlyCostAlertUsd);}
    @GetMapping("/posts/{id}") public BlogPost post(@PathVariable UUID id){return admin.post(id);}
    @GetMapping("/posts/{id}/revisions") public List<BlogWriterRepository.Revision> revisions(@PathVariable UUID id){return admin.revisions(id);}
    @PostMapping("/posts/{id}/revise") public BlogWriterRepository.Revision revise(@PathVariable UUID id,@Valid @RequestBody ReviseRequest request,@AuthenticationPrincipal UUID actor){if(!apiKeyConfigured)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI writer is not configured");return revise.revise(id,actor,request.instruction());}
    @PutMapping("/posts/{id}") public DraftActionResponse edit(@PathVariable UUID id,@Valid @RequestBody ManualEditRequest request,@AuthenticationPrincipal UUID actor){admin.manualEdit(id,actor,request.title(),request.body(),request.excerpt(),sanitizer);return new DraftActionResponse(id,"REVIEW");}
    @PostMapping("/posts/{id}/revisions/{version}/restore") public DraftActionResponse restore(@PathVariable UUID id,@PathVariable int version,@AuthenticationPrincipal UUID actor){admin.restore(id,version,actor);return new DraftActionResponse(id,"REVIEW");}
    @PostMapping("/posts/{id}/publish") @ResponseStatus(HttpStatus.NO_CONTENT) public void publish(@PathVariable UUID id){posts.publish(id);}
    @PostMapping("/posts/{id}/reject") public DraftActionResponse reject(@PathVariable UUID id){admin.reject(id);return new DraftActionResponse(id,"ARCHIVED");}
    private SettingsResponse settingsResponse(){
        var settings=admin.settings();
        if(!settings.enabled())return new SettingsResponse(settings,apiKeyConfigured,writerEnabled,null);
        var now=java.time.ZonedDateTime.now(settings.timezone());
        var date=now.toLocalDate();
        // Match scheduler: once localTime has been reached today (or already scheduled), next run is tomorrow.
        if(date.equals(settings.lastScheduledDate())||!now.toLocalTime().isBefore(settings.localTime()))date=date.plusDays(1);
        return new SettingsResponse(settings,apiKeyConfigured,writerEnabled,date.atTime(settings.localTime()).atZone(settings.timezone()).toInstant());
    }
}
