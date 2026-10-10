package com.knowledgegym.presentation;

import com.knowledgegym.english.application.EnglishCatalog;
import com.knowledgegym.english.application.EnglishReferenceResponses;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/** Authoring/browser-test artifact only, under ignored build output; not an HTTP endpoint. */
class EnglishCatalogEvidenceTest {
    @Test void exportsCompiledOriginalBankForAudioAndBrowserVerification() throws Exception {
        var catalog = new EnglishCatalog();
        var rows = catalog.list().stream().map(exercise -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", exercise.id()); row.put("skill", exercise.skill().name()); row.put("title", exercise.title());
            row.put("focus", exercise.focus()); row.put("minutes", exercise.minutes()); row.put("minimumWords", exercise.minimumWords());
            row.put("prompt", exercise.prompt()); row.put("passage", exercise.passage()); row.put("audioPath", exercise.audioPath());
            row.put("transcript", exercise.transcript()); row.put("items", exercise.items()); row.put("checklist", exercise.checklist());
            row.put("parts", catalog.parts(exercise.id())); row.put("scope", catalog.scope(exercise.id())); row.put("curriculum", catalog.curriculum(exercise.id()));
            row.put("referenceResponse", EnglishReferenceResponses.find(exercise.id()).orElse(null));
            return row;
        }).toList();
        assertThat(rows).hasSize(47);
        var output = Path.of("build/reports/english/catalog-fixture.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(rows));
    }
}
