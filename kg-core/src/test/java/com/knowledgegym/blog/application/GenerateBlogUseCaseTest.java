package com.knowledgegym.blog.application;

import com.knowledgegym.blog.application.template.ComparisonTemplate;
import com.knowledgegym.blog.application.template.DeepDiveTemplate;
import com.knowledgegym.blog.domain.model.BlogPost;
import com.knowledgegym.blog.domain.port.*;
import com.knowledgegym.blog.domain.service.QualityScorer;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GenerateBlogUseCaseTest {
    private final UUID sourceId=UUID.randomUUID(),postId=UUID.randomUUID(),queueId=UUID.randomUUID();
    private final AiWriterPort.Source source=new AiWriterPort.Source(sourceId,"Spring transaction isolation","Spring source summary","https://example.org/spring");
    private final String body="<h2>What</h2><p>"+"Useful evidence-based explanation. ".repeat(50)+"</p><h2>How</h2><pre><code>transaction example</code></pre>";
    private final AiWriterPort.Completion completion=new AiWriterPort.Completion("Understanding Spring transaction isolation",body,"An evidence-based introduction to Spring transactions.","Spring transactions","Transaction isolation explained",List.of("spring"),List.of(sourceId),"test-model",100,800,0.003);

    @Test void manualReviewStoresDraftWithoutPublishing(){
        var settings=mock(BlogScheduleSettingsPort.class);when(settings.current()).thenReturn(settings(false,85));
        var writers=mock(BlogWriterRepository.class);when(writers.selectSources(8)).thenReturn(List.of(source));when(writers.createReviewDraft(any(),anyList(),anyInt(),eq(queueId))).thenReturn(post());
        var ai=mock(AiWriterPort.class);when(ai.draft(anyString(),any(),anyList(),anyString())).thenReturn(completion);
        var posts=mock(BlogPostRepository.class);

        var result=useCase(ai,writers,settings).generate(job(),UUID.randomUUID());

        assertEquals("REVIEW",result.post().status());assertEquals(1,result.sourceCount());assertTrue(result.qualityScore()>0);
        verify(settings,never()).publishIfStillQualified(any(),anyInt());verify(writers).createReviewDraft(any(),eq(List.of(sourceId)),anyInt(),eq(queueId));
    }

    @Test void qualifiedAutoModePublishesButReadsPolicyAfterGeneration(){
        var settings=mock(BlogScheduleSettingsPort.class);when(settings.current()).thenReturn(settings(true,80));
        var writers=mock(BlogWriterRepository.class);when(writers.selectSources(8)).thenReturn(List.of(source));when(writers.createReviewDraft(any(),anyList(),anyInt(),eq(queueId))).thenReturn(post());
        var ai=mock(AiWriterPort.class);when(ai.draft(anyString(),any(),anyList(),anyString())).thenReturn(completion);
        var posts=mock(BlogPostRepository.class);

        var result=useCase(ai,writers,settings).generate(job(),UUID.randomUUID());

        assertTrue(result.qualityScore()>=80);verify(settings).publishIfStillQualified(postId,result.qualityScore());
    }

    @Test void policyChangedToManualWhileModelRunsPreventsAutopublish(){
        var policy=new AtomicReference<>(settings(true,80));var settings=mock(BlogScheduleSettingsPort.class);when(settings.current()).thenAnswer(call->policy.get());
        var writers=mock(BlogWriterRepository.class);when(writers.selectSources(8)).thenReturn(List.of(source));when(writers.createReviewDraft(any(),anyList(),anyInt(),eq(queueId))).thenReturn(post());
        var ai=mock(AiWriterPort.class);when(ai.draft(anyString(),any(),anyList(),anyString())).thenAnswer(call->{policy.set(settings(false,85));return completion;});
        var posts=mock(BlogPostRepository.class);

        useCase(ai,writers,settings).generate(job(),UUID.randomUUID());

        verify(settings,never()).publishIfStillQualified(any(),anyInt());
    }

    @Test void untrustedSourceIdIsRejectedBeforePersisting(){
        var settings=mock(BlogScheduleSettingsPort.class);when(settings.current()).thenReturn(settings(false,85));
        var writers=mock(BlogWriterRepository.class);when(writers.selectSources(8)).thenReturn(List.of(source));
        var ai=mock(AiWriterPort.class);when(ai.draft(anyString(),any(),anyList(),anyString())).thenReturn(new AiWriterPort.Completion("Valid technical title",body,"Valid excerpt",null,null,List.of(),List.of(UUID.randomUUID()),"test",1,1,0));
        assertThrows(IllegalArgumentException.class,()->useCase(ai,writers,settings).generate(job(),UUID.randomUUID()));
        verify(writers,never()).createReviewDraft(any(),anyList(),anyInt(),any());
    }

    private GenerateBlogUseCase useCase(AiWriterPort ai,BlogWriterRepository writer,BlogScheduleSettingsPort settings){
        return new GenerateBlogUseCase(ai,writer,settings,html->html,new QualityScorer(),List.of(new DeepDiveTemplate(),new ComparisonTemplate()));
    }
    private BlogScheduleSettingsPort.Settings settings(boolean auto,int threshold){return new BlogScheduleSettingsPort.Settings(false,LocalTime.NOON,ZoneId.of("Asia/Jakarta"),1,auto?BlogScheduleSettingsPort.PublishPolicy.AUTO_PUBLISH_QUALIFIED:BlogScheduleSettingsPort.PublishPolicy.MANUAL_REVIEW,threshold,null,null);}
    private BlogScheduleSettingsPort.Job job(){return new BlogScheduleSettingsPort.Job(queueId,"Spring transaction isolation",null,"test",null,"GENERATING",1,null,null);}
    private BlogPost post(){return new BlogPost(postId,null,"AI",completion.title(),"spring-transactions",completion.bodyHtml(),completion.excerpt(),null,null,"REVIEW",null,0,0,List.of(),Instant.now());}
}
