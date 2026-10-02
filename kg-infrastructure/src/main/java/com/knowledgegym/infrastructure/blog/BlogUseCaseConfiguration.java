package com.knowledgegym.infrastructure.blog;

import com.knowledgegym.blog.application.BlogPostsUseCase;
import com.knowledgegym.blog.application.CollectItemsUseCase;
import com.knowledgegym.blog.application.GenerateBlogUseCase;
import com.knowledgegym.blog.application.ReviseBlogDraftUseCase;
import com.knowledgegym.blog.application.BlogWriterAdminUseCase;
import com.knowledgegym.blog.application.template.BlogTemplate;
import com.knowledgegym.blog.application.template.DeepDiveTemplate;
import com.knowledgegym.blog.application.template.ComparisonTemplate;
import com.knowledgegym.blog.domain.port.AiWriterPort;
import com.knowledgegym.blog.domain.port.BlogScheduleSettingsPort;
import com.knowledgegym.blog.domain.port.BlogPostRepository;
import com.knowledgegym.blog.domain.port.BlogWriterRepository;
import com.knowledgegym.blog.domain.service.QualityScorer;
import com.knowledgegym.blog.domain.port.CollectorFeed;
import com.knowledgegym.blog.domain.port.CollectorRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;

@Configuration
public class BlogUseCaseConfiguration {
    @Bean CollectItemsUseCase collectItemsUseCase(CollectorRepository repository, CollectorFeed feed) {
        return new CollectItemsUseCase(repository, feed);
    }
    @Bean BlogPostsUseCase blogPostsUseCase(BlogPostRepository posts, BlogPostsUseCase.HtmlSanitizer sanitizer) {
        return new BlogPostsUseCase(posts, sanitizer);
    }
    @Bean DeepDiveTemplate deepDiveTemplate(){return new DeepDiveTemplate();}
    @Bean ComparisonTemplate comparisonTemplate(){return new ComparisonTemplate();}
    @Bean QualityScorer qualityScorer(){return new QualityScorer();}
    @Bean GenerateBlogUseCase generateBlogUseCase(AiWriterPort ai,BlogWriterRepository repository,BlogScheduleSettingsPort settings,
            BlogPostsUseCase.HtmlSanitizer sanitizer,QualityScorer scorer,List<BlogTemplate> templates){
        return new GenerateBlogUseCase(ai,repository,settings,sanitizer,scorer,templates);
    }
    @Bean ReviseBlogDraftUseCase reviseBlogDraftUseCase(AiWriterPort ai,BlogWriterRepository repository,BlogPostsUseCase.HtmlSanitizer sanitizer,
            QualityScorer scorer,BlogScheduleSettingsPort settings,@Value("${app.blog.writer.daily-request-limit:20}") int requests,
            @Value("${app.blog.writer.daily-token-limit:80000}") int tokens,@Value("${app.blog.writer.daily-cost-limit-usd:10}") double cost){return new ReviseBlogDraftUseCase(ai,repository,sanitizer,scorer,settings,requests,tokens,cost);}
    @Bean BlogWriterAdminUseCase blogWriterAdminUseCase(BlogScheduleSettingsPort settings,BlogWriterRepository repository,QualityScorer scorer,
            @Value("${app.blog.writer.daily-request-limit:20}") int requests,@Value("${app.blog.writer.daily-token-limit:80000}") int tokens,
            @Value("${app.blog.writer.daily-cost-limit-usd:10}") double cost){return new BlogWriterAdminUseCase(settings,repository,scorer,requests,tokens,cost);}
}
