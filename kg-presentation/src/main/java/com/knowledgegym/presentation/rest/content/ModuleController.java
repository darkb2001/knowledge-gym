package com.knowledgegym.presentation.rest.content;

import com.knowledgegym.content.application.CatalogQueryUseCase;
import com.knowledgegym.presentation.rest.content.dto.ModuleDTO;
import com.knowledgegym.shared.application.NotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/modules")
public class ModuleController {

    private final CatalogQueryUseCase catalogQueryUseCase;

    public ModuleController(CatalogQueryUseCase catalogQueryUseCase) {
        this.catalogQueryUseCase = catalogQueryUseCase;
    }

    @GetMapping
    @Cacheable(value = "modules", key = "#topicId == null ? 'all' : #topicId.toString()")
    public List<ModuleDTO> list(@RequestParam(required = false) UUID topicId) {
        return catalogQueryUseCase.listModules(topicId).stream().map(ModuleDTO::from).toList();
    }

    @GetMapping("/{id}")
    public ModuleDTO detail(@PathVariable UUID id) {
        return catalogQueryUseCase.findModuleById(id)
                .map(ModuleDTO::from)
                .orElseThrow(() -> new NotFoundException("Module không tồn tại: " + id));
    }
}
