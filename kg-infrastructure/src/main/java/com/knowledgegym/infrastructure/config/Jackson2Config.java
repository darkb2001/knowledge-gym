package com.knowledgegym.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Boot 4 registers a Jackson <b>3</b> {@code tools.jackson.databind.ObjectMapper} for HTTP
 * message conversion. Blog/collector code still injects Jackson <b>2</b>
 * ({@code com.fasterxml.jackson.databind.ObjectMapper}), so without this bean the context fails
 * to start with "No qualifying bean of type ObjectMapper".
 *
 * <p>Keep the two ObjectMappers deliberately separate: migrating every call site to Jackson 3 is
 * a distinct change, and Boot continues to own the Jackson 3 bean used on the HTTP path.
 */
@Configuration
public class Jackson2Config {

    @Bean
    ObjectMapper jackson2ObjectMapper() {
        return JsonMapper.builder()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .findAndAddModules()
                .build();
    }
}
