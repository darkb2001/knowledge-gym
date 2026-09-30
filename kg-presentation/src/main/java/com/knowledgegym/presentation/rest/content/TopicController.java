package com.knowledgegym.presentation.rest.content;

import com.knowledgegym.content.application.CatalogQueryUseCase;
import com.knowledgegym.presentation.rest.content.dto.TopicDTO;
import com.knowledgegym.shared.application.NotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/topics")
public class TopicController {

    private final CatalogQueryUseCase catalogQueryUseCase;

    public TopicController(CatalogQueryUseCase catalogQueryUseCase) {
        this.catalogQueryUseCase = catalogQueryUseCase;
    }

    @GetMapping
    @Cacheable(value = "topics", key = "'all'")
    public List<TopicDTO> list() {
        return catalogQueryUseCase.listTopics().stream().map(TopicDTO::from).toList();
    }

    @GetMapping("/{slug}")
    public TopicDTO detail(@PathVariable String slug) {
        return catalogQueryUseCase.findTopicBySlug(slug)
                .map(TopicDTO::from)
                .orElseThrow(() -> new NotFoundException("Topic không tồn tại: " + slug));
    }
}
