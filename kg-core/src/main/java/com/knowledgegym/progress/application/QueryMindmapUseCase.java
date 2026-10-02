package com.knowledgegym.progress.application;

import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.progress.domain.port.UserProgressRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/** Builds a per-user module graph from the catalog and mastery data. */
public class QueryMindmapUseCase {
    private final ModuleRepository modules;
    private final UserProgressRepository progress;

    public QueryMindmapUseCase(ModuleRepository modules, UserProgressRepository progress) {
        this.modules = modules;
        this.progress = progress;
    }

    public record Node(UUID id, String name, String slug, UUID topicId,
                       long questionCount, BigDecimal masteryPct) {}
    public record Edge(String source, String target, String relation) {}
    public record Mindmap(List<Node> nodes, List<Edge> edges) {}

    @Transactional(readOnly = true)
    public Mindmap execute(UUID userId) {
        var masteryByModule = progress.findAllByUser(userId).stream()
                .collect(Collectors.toMap(p -> p.moduleId(), p -> p.masteryPct(), (a, b) -> a));
        var questionCounts = modules.countQuestionsByModule();
        var orderedModules = modules.findAllOrdered();
        var nodes = orderedModules.stream()
                .filter(module -> module.isActive())
                .map(module -> new Node(module.getId(), module.getName(), module.getSlug(),
                        module.getTopicId(), questionCounts.getOrDefault(module.getId(), 0L),
                        masteryByModule.getOrDefault(module.getId(), BigDecimal.ZERO)))
                .toList();

        // The catalog currently has topic membership but no explicit prerequisite/tag relation.
        // Connect modules in the same topic as a lightweight, honest "same-topic" relation.
        Map<UUID, List<Node>> byTopic = new HashMap<>();
        nodes.stream().filter(node -> node.topicId() != null)
                .forEach(node -> byTopic.computeIfAbsent(node.topicId(), ignored -> new ArrayList<>()).add(node));
        List<Edge> edges = new ArrayList<>();
        byTopic.values().forEach(group -> {
            group.sort(Comparator.comparing(Node::slug));
            for (int i = 1; i < group.size(); i++) {
                edges.add(new Edge(group.get(0).id().toString(), group.get(i).id().toString(), "same-topic"));
            }
        });
        return new Mindmap(nodes, edges);
    }
}
