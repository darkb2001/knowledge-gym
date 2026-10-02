package com.knowledgegym.infrastructure.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JPA + async config — ở infrastructure layer để presentation KHÔNG import
 * jakarta.persistence/hibernate (đảm bảo PresentationLayerArchTest).
 */
@Configuration
@EntityScan(basePackages = "com.knowledgegym.infrastructure")
@EnableJpaRepositories(basePackages = "com.knowledgegym.infrastructure")
@EnableAsync
@EnableScheduling
public class PersistenceConfig {

    /**
     * Dùng bởi {@code ContentImportJobService} để bọc cả lần import trong 1 transaction —
     * không đặt {@code @Transactional} trên use case (kg-core thuần Java).
     */
    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}