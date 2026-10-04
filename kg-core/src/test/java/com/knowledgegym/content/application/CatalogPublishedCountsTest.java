package com.knowledgegym.content.application;

import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.content.domain.port.TopicRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CatalogPublishedCountsTest {
    @Test
    void moduleRemovalStillRefusesAnAllDraftModule() {
        var topics = mock(TopicRepository.class);
        var modules = mock(ModuleRepository.class);
        UUID id = UUID.randomUUID();
        var module = new ModuleRef("Draft module", "draft-module", 1);
        module.setId(id);
        when(modules.findById(id)).thenReturn(Optional.of(module));
        when(modules.countQuestions(id)).thenReturn(3L);
        assertThatThrownBy(() -> new ManageCatalogUseCase(topics, modules).deleteModule(id))
                .isInstanceOf(com.knowledgegym.shared.application.ConflictException.class);
        verify(modules, never()).deleteById(any());
    }

    @Test
    void listAndDetailUsePublishedCountsWithoutWeakeningRemovalGuards() {
        var topics = mock(TopicRepository.class);
        when(topics.findAllOrdered()).thenReturn(List.of());
        var modules = mock(ModuleRepository.class);
        var module = new ModuleRef("Module", "module", 1);
        module.setId(UUID.randomUUID());
        when(modules.findAllOrdered()).thenReturn(List.of(module));
        when(modules.findById(module.getId())).thenReturn(Optional.of(module));
        when(modules.countPublishedQuestionsByModule()).thenReturn(Map.of(module.getId(), 2L));
        when(modules.countPublishedQuestions(module.getId())).thenReturn(2L);
        var useCase = new CatalogQueryUseCase(topics, modules);
        assertThat(useCase.listModules(null).getFirst().questionCount()).isEqualTo(2);
        assertThat(useCase.findModuleById(module.getId()).orElseThrow().questionCount()).isEqualTo(2);
        verify(modules, never()).countQuestions(any());
        verify(modules, never()).countQuestionsByModule();
    }
}
