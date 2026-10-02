package com.knowledgegym.presentation.rest;

import com.knowledgegym.progress.application.QueryMindmapUseCase;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MindmapController {
    private final QueryMindmapUseCase queryMindmap;

    public MindmapController(QueryMindmapUseCase queryMindmap) {
        this.queryMindmap = queryMindmap;
    }

    @GetMapping("/mindmap")
    public QueryMindmapUseCase.Mindmap mindmap(@AuthenticationPrincipal UUID userId) {
        return queryMindmap.execute(userId);
    }
}
