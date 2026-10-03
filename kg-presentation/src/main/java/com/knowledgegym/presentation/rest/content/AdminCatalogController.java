package com.knowledgegym.presentation.rest.content;

import com.knowledgegym.content.application.CatalogQueryUseCase;
import com.knowledgegym.content.application.ManageCatalogUseCase;
import com.knowledgegym.presentation.rest.content.dto.ModuleDTO;
import com.knowledgegym.presentation.rest.content.dto.TopicDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/admin/content")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogController {
    private final ManageCatalogUseCase manage;
    private final CatalogQueryUseCase catalog;
    public AdminCatalogController(ManageCatalogUseCase manage, CatalogQueryUseCase catalog) {
        this.manage = manage; this.catalog = catalog;
    }
    public record TopicCreate(@NotBlank String name, @NotBlank String slug, String description, @NotNull @Min(0) Integer displayOrder) {
        ManageCatalogUseCase.TopicPatch patch() { return new ManageCatalogUseCase.TopicPatch(name, slug, description, displayOrder); }
    }
    public record TopicUpdate(String name, String slug, String description, Integer displayOrder) {
        ManageCatalogUseCase.TopicPatch patch() { return new ManageCatalogUseCase.TopicPatch(name, slug, description, displayOrder); }
    }
    public record ModuleCreate(@NotNull UUID topicId, @NotBlank String name, @NotBlank String slug, String description, @NotNull @Min(0) Integer displayOrder) {
        ManageCatalogUseCase.ModulePatch patch() { return new ManageCatalogUseCase.ModulePatch(topicId, name, slug, description, displayOrder); }
    }
    public record ModuleUpdate(UUID topicId, String name, String slug, String description, Integer displayOrder) {
        ManageCatalogUseCase.ModulePatch patch() { return new ManageCatalogUseCase.ModulePatch(topicId, name, slug, description, displayOrder); }
    }
    @PostMapping("/topics") @ResponseStatus(HttpStatus.CREATED)
    @CacheEvict(value = {"topics", "modules", "questions"}, allEntries = true)
    public TopicDTO createTopic(@Valid @RequestBody TopicCreate request) {
        var topic = manage.createTopic(request.patch());
        return new TopicDTO(topic.getId(), topic.getSlug(), topic.getName(), topic.getDescription(), topic.getDisplayOrder(), 0);
    }
    @PatchMapping("/topics/{id}")
    @CacheEvict(value = {"topics", "modules", "questions"}, allEntries = true)
    public TopicDTO updateTopic(@PathVariable UUID id, @Valid @RequestBody TopicUpdate request) {
        var topic = manage.updateTopic(id, request.patch());
        return catalog.findTopicBySlug(topic.getSlug()).map(TopicDTO::from).orElseThrow();
    }
    @DeleteMapping("/topics/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @CacheEvict(value = {"topics", "modules", "questions"}, allEntries = true)
    public void deleteTopic(@PathVariable UUID id) { manage.deleteTopic(id); }
    @PostMapping("/modules") @ResponseStatus(HttpStatus.CREATED)
    @CacheEvict(value = {"topics", "modules", "questions"}, allEntries = true)
    public ModuleDTO createModule(@Valid @RequestBody ModuleCreate request) {
        var module = manage.createModule(request.patch());
        return catalog.findModuleById(module.getId()).map(ModuleDTO::from).orElseThrow();
    }
    @PatchMapping("/modules/{id}")
    @CacheEvict(value = {"topics", "modules", "questions"}, allEntries = true)
    public ModuleDTO updateModule(@PathVariable UUID id, @Valid @RequestBody ModuleUpdate request) {
        manage.updateModule(id, request.patch());
        return catalog.findModuleById(id).map(ModuleDTO::from).orElseThrow();
    }
    @DeleteMapping("/modules/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @CacheEvict(value = {"topics", "modules", "questions"}, allEntries = true)
    public void deleteModule(@PathVariable UUID id) { manage.deleteModule(id); }
}
