package com.knowledgegym.presentation;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. Default package = com.knowledgegym.presentation (module's root package).
 * PersistenceConfig trong kg-infrastructure (package com.knowledgegym.infrastructure.config)
 * được picked up bởi @ComponentScan trên @SpringBootApplication — nhưng ARCH TEST kiểm tra
 * class-level import nên cần đảm bảo presentation KHÔNG import jakarta.persistence:
 * tất cả JPA/Entity/Repo nằm trong kg-infrastructure, KHÔNG import vào presentation Java source.
 */
@SpringBootApplication(scanBasePackages = "com.knowledgegym")
public class KnowledgeGymApplication {

    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(KnowledgeGymApplication.class, args);
    }
}