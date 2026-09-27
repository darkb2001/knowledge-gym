package com.knowledgegym.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

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
}