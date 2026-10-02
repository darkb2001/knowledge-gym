package com.knowledgegym.progress.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.progress.domain.model.UserProgress;
import com.knowledgegym.progress.domain.port.UserProgressRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QueryMindmapUseCaseTest {
    private final ModuleRepository modules = mock(ModuleRepository.class);
    private final UserProgressRepository progress = mock(UserProgressRepository.class);
    private final QueryMindmapUseCase useCase = new QueryMindmapUseCase(modules, progress);

    @Test
    void buildsUserMasteryNodesAndSameTopicEdgesWithoutInventingPrerequisites() {
        UUID userId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        ModuleRef first = module("Java basics", "01-java", topicId);
        ModuleRef second = module("Collections", "02-collections", topicId);
        ModuleRef otherTopic = module("SQL", "03-sql", UUID.randomUUID());
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        first.setId(firstId);
        second.setId(secondId);
        otherTopic.setId(otherId);
        when(modules.findAllOrdered()).thenReturn(List.of(first, second, otherTopic));
        when(modules.countQuestionsByModule()).thenReturn(Map.of(firstId, 4L, secondId, 9L));
        when(progress.findAllByUser(userId)).thenReturn(List.of(new UserProgress(userId, secondId,
                new BigDecimal("75.00"), 4, 3, Instant.EPOCH)));

        var result = useCase.execute(userId);

        assertThat(result.nodes()).hasSize(3);
        assertThat(result.nodes()).filteredOn(node -> node.id().equals(firstId))
                .singleElement().satisfies(node -> {
                    assertThat(node.questionCount()).isEqualTo(4L);
                    assertThat(node.masteryPct()).isEqualByComparingTo(BigDecimal.ZERO);
                });
        assertThat(result.nodes()).filteredOn(node -> node.id().equals(secondId))
                .singleElement().satisfies(node -> assertThat(node.masteryPct())
                        .isEqualByComparingTo("75.00"));
        assertThat(result.nodes()).filteredOn(node -> node.id().equals(otherId))
                .singleElement().satisfies(node -> assertThat(node.questionCount()).isZero());
        assertThat(result.edges()).containsExactly(new QueryMindmapUseCase.Edge(
                firstId.toString(), secondId.toString(), "same-topic"));
    }

    private static ModuleRef module(String name, String slug, UUID topicId) {
        ModuleRef module = new ModuleRef(name, slug, 1);
        module.setTopicId(topicId);
        return module;
    }
}
