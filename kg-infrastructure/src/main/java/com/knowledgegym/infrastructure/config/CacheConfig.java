package com.knowledgegym.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * L1 in-memory cache cho nội dung câu hỏi (đọc nhiều, ghi rất ít).
 *
 * Đặt ở kg-infrastructure vì `CaffeineCacheManager` cần dependency caffeine
 * (kg-presentation không kéo caffeine). `@EnableCaching` đặt ở presentation — nơi `@Cacheable`
 * thực sự được dùng — để proxy không cắt ngang ranh giới module.
 *
 * Đây là L1, không phải Redis: cache nội dung tĩnh, mất khi restart là chấp nhận được,
 * và tránh round-trip mạng cho endpoint đọc nhiều nhất.
 */
@Configuration
public class CacheConfig {

    @Bean
    CacheManager cacheManager(@Value("${app.cache.questions.max-size:1000}") long maxSize,
                              @Value("${app.cache.questions.ttl:30m}") Duration ttl) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(List.of("questions", "topics", "modules", "tracks"));
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttl)
                .recordStats());
        return manager;
    }
}
