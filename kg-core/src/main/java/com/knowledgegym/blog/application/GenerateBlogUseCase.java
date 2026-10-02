package com.knowledgegym.blog.application;

import com.knowledgegym.blog.application.template.BlogTemplate;
import com.knowledgegym.blog.domain.model.BlogPost;
import com.knowledgegym.blog.domain.port.AiWriterPort;
import com.knowledgegym.blog.domain.port.BlogScheduleSettingsPort;
import com.knowledgegym.blog.domain.port.BlogWriterRepository;
import com.knowledgegym.blog.domain.service.QualityScorer;
import java.util.List;
import java.util.UUID;

/** Generates only from application-supplied public/shared evidence. No user notes or provider tools are available. */
public final class GenerateBlogUseCase {
    private final AiWriterPort writer;
    private final BlogWriterRepository repository;
    private final BlogScheduleSettingsPort settings;
    private final BlogPostsUseCase.HtmlSanitizer sanitizer;
    private final QualityScorer scorer;
    private final List<BlogTemplate> templates;

    public GenerateBlogUseCase(AiWriterPort writer, BlogWriterRepository repository, BlogScheduleSettingsPort settings,
                               BlogPostsUseCase.HtmlSanitizer sanitizer,
                               QualityScorer scorer, List<BlogTemplate> templates) {
        this.writer=writer; this.repository=repository; this.settings=settings;
        this.sanitizer=sanitizer; this.scorer=scorer; this.templates=templates;
    }

    public Result generate(BlogScheduleSettingsPort.Job job, UUID runId) {
        List<AiWriterPort.Source> sources=repository.selectSources(8);
        if(sources.isEmpty())throw new IllegalStateException("No collected knowledge sources are available");
        String topic=job.topic()==null||job.topic().isBlank()||job.topic().startsWith("Daily Knowledge Gym")?sources.getFirst().title():job.topic();
        BlogTemplate template=topic.toLowerCase().contains(" vs ")||topic.toLowerCase().contains("versus")
                ?templates.stream().filter(t->t.name().equals("COMPARISON")).findFirst().orElse(templates.getFirst())
                :templates.stream().filter(t->t.name().equals("DEEP_DIVE")).findFirst().orElse(templates.getFirst());
        AiWriterPort.Completion raw=template.draft(writer,topic,job.angle(),sources);
        settings.settleGlobalBudget(runId,raw.tokensUsed(),raw.costUsd());
        List<UUID> sourceIds=validatedSourceIds(raw.sourceIds(),sources);
        String body=sanitizer.sanitizeGenerated(raw.bodyHtml());
        String title=clean(raw.title(),200);
        if(title.length()<8||body.length()<300)throw new IllegalArgumentException("Generated draft failed required title/body checks");
        if(!body.toLowerCase().contains("<h2"))throw new IllegalArgumentException("Generated draft must contain section headings");
        String excerpt=clean(raw.excerpt(),300);
        if(excerpt.isBlank())excerpt=plainText(body).substring(0,Math.min(250,plainText(body).length()));
        String citationHtml=citations(sourceIds,sources);
        body=sanitizer.sanitize(body+citationHtml);
        AiWriterPort.Completion safe=new AiWriterPort.Completion(title,body,excerpt,clean(raw.seoTitle(),200),
                clean(raw.seoDescription(),300),raw.seoKeywords().stream().filter(s->s!=null&&!s.isBlank()).limit(15).toList(),
                sourceIds,raw.model(),raw.inputTokens(),raw.outputTokens(),raw.costUsd());
        int score=scorer.score(title,body,excerpt,sourceIds.size());
        BlogPost post=repository.createReviewDraft(safe,sourceIds,score,job.id());
        // Re-read the policy after the provider returns. A switch to MANUAL_REVIEW takes effect immediately.
        var policy=settings.current();
        boolean hardQualified=!sourceIds.isEmpty()&&body.length()>=300&&body.toLowerCase().contains("<h2")&&!title.isBlank();
        if(policy.policy()==BlogScheduleSettingsPort.PublishPolicy.AUTO_PUBLISH_QUALIFIED&&hardQualified&&score>=policy.qualityThreshold())settings.publishIfStillQualified(post.id(),score);
        return new Result(post,score,sourceIds.size(),safe.tokensUsed(),safe.costUsd(),template.name());
    }

    private static List<UUID> validatedSourceIds(List<UUID> ids,List<AiWriterPort.Source> sources) {
        var allowed=sources.stream().map(AiWriterPort.Source::id).collect(java.util.stream.Collectors.toSet());
        if(ids==null||ids.isEmpty()||ids.stream().anyMatch(id->id==null||!allowed.contains(id)))
            throw new IllegalArgumentException("AI returned missing or ungrounded source IDs");
        return ids.stream().distinct().limit(8).toList();
    }
    private static String citations(List<UUID> ids,List<AiWriterPort.Source> sources) {
        var byId=sources.stream().collect(java.util.stream.Collectors.toMap(AiWriterPort.Source::id,s->s));
        StringBuilder html=new StringBuilder("<h2>Sources</h2><ul>");
        for(UUID id:ids){var source=byId.get(id); if(source!=null&&source.canonicalUrl().startsWith("https://"))
            html.append("<li><a href=\"").append(escape(source.canonicalUrl())).append("\" rel=\"nofollow noopener\">").append(escape(source.title())).append("</a></li>");}
        return html.append("</ul>").toString();
    }
    private static String escape(String value){return value.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;").replace(">","&gt;");}
    private static String clean(String value,int max){if(value==null)return "";String s=value.trim();return s.substring(0,Math.min(max,s.length()));}
    private static String plainText(String html){return html.replaceAll("<[^>]*>"," ").replaceAll("\\s+"," ").trim();}
    public record Result(BlogPost post,int qualityScore,int sourceCount,int tokensUsed,double costUsd,String template){}
}
