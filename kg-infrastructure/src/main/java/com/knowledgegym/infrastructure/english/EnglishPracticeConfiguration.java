package com.knowledgegym.infrastructure.english;
import com.knowledgegym.english.application.EnglishCatalog;
import com.knowledgegym.english.application.EnglishPracticeUseCase;
import com.knowledgegym.english.domain.port.EnglishAttemptRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EnglishPracticeConfiguration {
    @Bean EnglishCatalog englishCatalog() { return new EnglishCatalog(); }
    @Bean EnglishPracticeUseCase englishPracticeUseCase(EnglishAttemptRepository repository, EnglishCatalog catalog) {
        return new EnglishPracticeUseCase(repository, catalog);
    }
}
