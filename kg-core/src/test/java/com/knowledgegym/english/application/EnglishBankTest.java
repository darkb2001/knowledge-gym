package com.knowledgegym.english.application;

import com.knowledgegym.english.domain.model.EnglishExercise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.List;
import java.util.stream.Stream;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;
import static com.knowledgegym.english.domain.model.EnglishExercise.Skill.*;

class EnglishBankTest {
    private final EnglishCatalog catalog = new EnglishCatalog();
    static Stream<EnglishExercise> subjective() {
        return new EnglishCatalog().list().stream().filter(e -> e.items().isEmpty() && (e.skill() == WRITING || e.skill() == SPEAKING));
    }
    private static int words(String text) {
        return (int)Pattern.compile("[\\p{L}\\p{N}]+(?:['’-][\\p{L}\\p{N}]+)*").matcher(text).results().count();
    }
    @ParameterizedTest @MethodSource("subjective")
    void everyWritingAndSpeakingTaskHasAnOriginalWorkedResponse(EnglishExercise exercise) {
        var model = EnglishReferenceResponses.find(exercise.id()).orElseThrow();
        assertThat(model.text()).hasSizeGreaterThan(300).doesNotContain("TODO", "Lorem ipsum", "official answer key");
        assertThat(model.title()).isNotBlank();
        assertThat(model.notes()).isNotEmpty().allSatisfy(note -> {
            assertThat(note.vi()).isNotBlank(); assertThat(note.en()).isNotBlank();
        });
        if (exercise.skill() == WRITING) assertThat(words(model.text())).isGreaterThanOrEqualTo(exercise.minimumWords());
    }
    @Test void readingSectionHasFourPassagesFortyQuestionsAndOfficialLengthRange() {
        var exercise = catalog.get("reading-complete-practice-v1");
        var parts = catalog.parts(exercise.id());
        assertThat(exercise.minutes()).isEqualTo(60);
        assertThat(exercise.items()).hasSize(40);
        assertThat(parts).hasSize(4).allSatisfy(p -> assertThat(p.itemIds()).hasSize(10));
        int total = parts.stream().mapToInt(p -> words(p.passage())).sum();
        assertThat(total).as("Original four-passage word count").isBetween(1900, 2500);
        assertThat(parts.stream().flatMap(p -> p.itemIds().stream()).toList())
            .containsExactlyElementsOf(exercise.items().stream().map(EnglishExercise.Item::id).toList());
    }
    @Test void listeningSectionUsesEightNoticesThreeConversationsAndThreeTalks() {
        var exercise = catalog.get("listening-complete-practice-v1");
        var parts = catalog.parts(exercise.id());
        assertThat(exercise.minutes()).isEqualTo(40);
        assertThat(exercise.items()).hasSize(35);
        assertThat(parts).hasSize(7);
        assertThat(parts.stream().map(p -> p.itemIds().size()).toList()).containsExactly(8, 4, 4, 4, 5, 5, 5);
        assertThat(parts).allSatisfy(p -> assertThat(p.audioPath()).startsWith("/english/audio/").endsWith(".mp3"));
        assertThat(parts.stream().flatMap(p -> p.itemIds().stream()).toList())
            .containsExactlyElementsOf(exercise.items().stream().map(EnglishExercise.Item::id).toList());
        assertThat(exercise.prompt()).contains("not an official paper", "plays the recording once");
    }
    @Test void everyObjectiveItemHasFourDistinctOptionsAValidAnswerAndEvidence() {
        for (var exercise : catalog.list()) {
            assertThat(exercise.items().stream().map(EnglishExercise.Item::id).toList()).doesNotHaveDuplicates();
            for (var item : exercise.items()) {
                assertThat(item.options()).hasSize(4).doesNotHaveDuplicates();
                assertThat(item.correctIndex()).isBetween(0, 3);
                assertThat(item.explanation()).isNotBlank();
                assertThat(item.stem()).isNotBlank();
            }
        }
    }
    @Test void preservesOriginalWarmupsAndGivesRevisionsNewIdsAndDistinctTitles() {
        assertThat(catalog.list()).hasSize(39);
        assertThat(catalog.list().stream().map(EnglishExercise::id).toList()).doesNotHaveDuplicates();
        assertThat(catalog.list().stream().map(EnglishExercise::title).toList()).doesNotHaveDuplicates();
        assertThat(catalog.get("listening-dialogue-v1").items()).hasSize(3);
        assertThat(catalog.get("listening-community-event-v2").items()).hasSize(4);
        assertThat(catalog.get("listening-talk-v1").items()).hasSize(3);
        assertThat(catalog.get("listening-small-habits-v2").items()).hasSize(5);
        assertThat(catalog.get("listening-dialogue-v1").transcript())
            .isEqualTo(catalog.get("listening-community-event-v2").transcript());
        assertThat(catalog.scope("writing-email-v1")).isEqualTo("SHORT_PRACTICE");
        assertThat(catalog.scope("reading-complete-practice-v1")).isEqualTo("COMPLETE_SKILL");
        assertThat(catalog.scope("writing-course-enquiry-v1")).isEqualTo("TASK_PRACTICE");
        assertThat(EnglishReferenceResponses.find("not-registered")).isEmpty();
        assertThat(EnglishReferenceResponses.find("reading-complete-practice-v1")).isEmpty();
    }
    @Test void hcmusListeningHasTheOfficialSectionCountsButOnlyOriginalPracticeAudio() {
        var e = catalog.get("hcmus-listening-complete-v1");
        assertThat(e.items()).hasSize(20);
        assertThat(catalog.parts(e.id()).stream().map(p -> p.itemIds().size())).containsExactly(10, 5, 5);
        assertThat(catalog.parts(e.id()).stream().flatMap(p -> p.itemIds().stream())).containsExactlyElementsOf(e.items().stream().map(EnglishExercise.Item::id).toList());
        assertThat(catalog.scope(e.id())).isEqualTo("COMPLETE_SKILL");
        assertThat(e.prompt()).contains("not an institution paper").contains("Replay");
        assertThat(catalog.get("hcmus-listening-short-v1").transcript().split("Conversation ").length - 1).isEqualTo(10);
    }
    @Test void hcmusPreparationIsSeparateAndDoesNotPretendToBeAnOfficialFullMock() {
        assertThat(catalog.list().stream().filter(e -> catalog.curriculum(e.id()).equals("HCMUS_PREPARATION")).toList()).hasSize(8);
        assertThat(catalog.get("hcmus-vocabulary-v1").items()).hasSize(10);
        assertThat(catalog.get("hcmus-grammar-v1").items()).hasSize(15);
        assertThat(catalog.get("hcmus-cloze-v1").items()).hasSize(10);
        assertThat(catalog.get("hcmus-transformations-v1").items()).isEmpty();
        assertThat(EnglishReferenceResponses.find("hcmus-grammar-v1")).isEmpty();
        assertThat(EnglishReferenceResponses.find("hcmus-transformations-v1")).isPresent();
        assertThat(catalog.get("hcmus-speaking-v1").prompt()).contains("not a confirmed institution question bank");
    }
    @Test void writingIncludesLettersAndEssaysAndSpeakingIncludesAllThreeParts() {
        assertThat(catalog.list().stream().filter(e -> e.skill() == WRITING && catalog.curriculum(e.id()).equals("VSTEP")).toList()).hasSize(6)
            .allSatisfy(e -> assertThat(e.minimumWords()).isIn(120, 250));
        assertThat(catalog.list().stream().filter(e -> e.skill() == SPEAKING && catalog.curriculum(e.id()).equals("VSTEP")).toList()).hasSize(8);
        for (String part : List.of("Part 1", "Part 2", "Part 3"))
            assertThat(catalog.list().stream().filter(e -> e.skill() == SPEAKING && e.focus().startsWith(part)).toList()).hasSize(part.equals("Part 3") ? 2 : 3);
    }
}
