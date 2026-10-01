package com.knowledgegym.infrastructure.blog;

import com.knowledgegym.blog.application.BlogPostsUseCase;
import com.knowledgegym.blog.application.CollectItemsUseCase;
import com.knowledgegym.blog.domain.port.BlogPostRepository;
import com.knowledgegym.blog.domain.port.CollectorFeed;
import com.knowledgegym.blog.domain.port.CollectorRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BlogUseCaseConfiguration {
    @Bean CollectItemsUseCase collectItemsUseCase(CollectorRepository repository, CollectorFeed feed) {
        return new CollectItemsUseCase(repository, feed);
    }
    @Bean BlogPostsUseCase blogPostsUseCase(BlogPostRepository posts, BlogPostsUseCase.HtmlSanitizer sanitizer) {
        return new BlogPostsUseCase(posts, sanitizer);
    }
}
