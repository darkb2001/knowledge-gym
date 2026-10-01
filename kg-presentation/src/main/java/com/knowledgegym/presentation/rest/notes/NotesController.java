package com.knowledgegym.presentation.rest.notes;

import com.knowledgegym.notes.application.NotesUseCase;
import com.knowledgegym.notes.domain.model.Note;
import com.knowledgegym.notes.domain.model.SearchHit;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class NotesController {
    /** Only match UUID ids so literal routes like /notes/export never fall through to {id}. */
    private static final String NOTE_ID =
            "{id:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}";

    private final NotesUseCase notes;

    public NotesController(NotesUseCase notes) {
        this.notes = notes;
    }

    public record NoteRequest(UUID questionId, UUID moduleId, String noteType, String content, List<String> tags) {}
    public record NoteResponse(UUID id, UUID questionId, UUID moduleId, String noteType, String content,
                               List<String> tags, Instant createdAt, Instant updatedAt) {
        static NoteResponse of(Note note) {
            return new NoteResponse(note.id(), note.questionId(), note.moduleId(), note.noteType(),
                    note.content(), note.tags(), note.createdAt(), note.updatedAt());
        }
    }
    public record SearchHitResponse(String type, UUID id, String title, String excerpt) {
        static SearchHitResponse of(SearchHit hit) {
            return new SearchHitResponse(hit.type(), hit.id(), hit.title(), hit.excerpt());
        }
    }

    @GetMapping("/notes")
    public List<NoteResponse> list(@AuthenticationPrincipal UUID userId) {
        return notes.list(userId).stream().map(NoteResponse::of).toList();
    }

    @GetMapping("/notes/export")
    public ResponseEntity<String> export(@AuthenticationPrincipal UUID userId,
                                         @RequestParam(defaultValue = "md") String format) {
        if (!format.equalsIgnoreCase("md")) {
            throw new IllegalArgumentException("Chỉ hỗ trợ format=md");
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=notes.md")
                .header("Content-Type", "text/markdown; charset=" + StandardCharsets.UTF_8.name())
                .body(notes.exportMarkdown(userId));
    }

    @GetMapping("/notes/" + NOTE_ID)
    public NoteResponse get(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return NoteResponse.of(notes.get(userId, id));
    }

    @PostMapping("/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse create(@AuthenticationPrincipal UUID userId, @RequestBody NoteRequest request) {
        return NoteResponse.of(notes.create(
                userId, request.questionId(), request.moduleId(), request.noteType(), request.content(), request.tags()));
    }

    @PutMapping("/notes/" + NOTE_ID)
    public NoteResponse update(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                               @RequestBody NoteRequest request) {
        return NoteResponse.of(notes.update(
                userId, id, request.questionId(), request.moduleId(), request.noteType(), request.content(), request.tags()));
    }

    @DeleteMapping("/notes/" + NOTE_ID)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        notes.delete(userId, id);
    }

    @GetMapping("/search")
    public List<SearchHitResponse> search(@AuthenticationPrincipal UUID userId, @RequestParam String q) {
        return notes.search(userId, q).stream().map(SearchHitResponse::of).toList();
    }

    @GetMapping("/users/me/bookmarks")
    public List<NoteResponse> bookmarks(@AuthenticationPrincipal UUID userId) {
        return notes.bookmarks(userId).stream().map(NoteResponse::of).toList();
    }

    @PostMapping("/notes/" + NOTE_ID + "/convert")
    public Map<String, UUID> convert(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return Map.of("cardId", notes.convertToSrsCard(userId, id));
    }
}
