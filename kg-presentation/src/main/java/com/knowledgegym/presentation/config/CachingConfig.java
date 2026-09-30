package com.knowledgegym.presentation.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * `@EnableCaching` đặt ở presentation — nơi `@Cacheable` thực sự được dùng.
 * Đặt ở infrastructure sẽ khiến proxy cắt ngang ranh giới module: use case trong kg-core
 * bị bọc bởi proxy của infrastructure, làm mất tính "kg-core là Java thuần".
 */
@Configuration
@EnableCaching
public class CachingConfig {
}
