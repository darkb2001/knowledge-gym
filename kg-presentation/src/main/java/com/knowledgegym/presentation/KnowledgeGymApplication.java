package com.knowledgegym.presentation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Entry point — m1 chỉ để bootJar + /actuator/health.
 * Auth, Flyway, @EnableAsync/@EnableScheduling thêm ở m02/m03.
 *
 * ComponentScan giới hạn trong package presentation để enforce Clean Architecture
 * (kg-presentation → kg-core ← kg-infrastructure, controllers KHÔNG import JPA).
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.knowledgegym")
@EntityScan(basePackages = "com.knowledgegym.infrastructure")
public class KnowledgeGymApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeGymApplication.class, args);
    }
}
