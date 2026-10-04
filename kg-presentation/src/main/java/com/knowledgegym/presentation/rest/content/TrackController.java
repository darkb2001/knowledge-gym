package com.knowledgegym.presentation.rest.content;

import com.knowledgegym.content.application.CatalogQueryUseCase;
import com.knowledgegym.presentation.rest.content.dto.TrackDTO;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tracks")
public class TrackController {

    private final CatalogQueryUseCase catalogQueryUseCase;

    public TrackController(CatalogQueryUseCase catalogQueryUseCase) {
        this.catalogQueryUseCase = catalogQueryUseCase;
    }

    @GetMapping
    @Cacheable(value = "tracks", key = "'all'")
    public List<TrackDTO> list() {
        return catalogQueryUseCase.listTracks().stream().map(TrackDTO::from).toList();
    }
}
