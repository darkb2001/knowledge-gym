package com.knowledgegym.presentation.rest.notes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.knowledgegym.notes.application.NotesUseCase;
import com.knowledgegym.notes.domain.model.Note;
import com.knowledgegym.search.application.GlobalSearchUseCase;
import com.knowledgegym.shared.domain.model.SearchHit;
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

    /** Client gửi `highlight: {}` (hoặc object không còn giá trị nào) để xoá neo của note đã có. */
    private static final String CLEARED_HIGHLIGHT = "{}";
    private static final int MAX_QUOTE = 600;
    private static final int MAX_SESSION = 64;
    private static final int MAX_PATH = 300;

    /**
     * Tự dựng mapper riêng thay vì tiêm bean ObjectMapper của Spring: presentation layer
     * (và test slice của nó) không có bean đó, mà neo đoạn văn chỉ cần JSON thuần.
     */
    private static final ObjectMapper HIGHLIGHT_MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();
    /** Đọc lại đoạn neo đã lưu; field lạ không làm hỏng cả note. */
    private static final ObjectReader HIGHLIGHT_READER = HIGHLIGHT_MAPPER.readerFor(HighlightRange.class);

    private final NotesUseCase notes;
    private final GlobalSearchUseCase search;

    public NotesController(NotesUseCase notes, GlobalSearchUseCase search) {
        this.notes = notes;
        this.search = search;
    }

    /**
     * Đoạn văn được neo khi người dùng ghi chú trực tiếp trong lúc khám phá: câu trích nguyên văn,
     * vị trí ký tự trong khối nội dung, chỉ số khối và phiên đọc (để gom note theo từng lượt đọc).
     */
    public record HighlightRange(String quote, Integer start, Integer end, Integer blockIndex,
                                 String sessionId, String path) {}

    public record NoteRequest(UUID questionId, UUID moduleId, String noteType, String content,
                              HighlightRange highlight, List<String> tags) {}
    public record NoteResponse(UUID id, UUID questionId, UUID moduleId, String noteType, String content,
                               HighlightRange highlight, List<String> tags, Instant createdAt, Instant updatedAt) {}

    private NoteResponse toResponse(Note note) {
        return new NoteResponse(note.id(), note.questionId(), note.moduleId(), note.noteType(), note.content(),
                decodeHighlight(note.highlightRange()), note.tags(), note.createdAt(), note.updatedAt());
    }

    /** Chuẩn hoá đoạn neo từ client: cắt độ dài, bỏ chỉ số âm, rỗng hoàn toàn thì trả null. */
    private static HighlightRange sanitizeHighlight(HighlightRange raw) {
        if (raw == null) {
            return null;
        }
        String quote = truncate(raw.quote(), MAX_QUOTE);
        String sessionId = truncate(raw.sessionId(), MAX_SESSION);
        String path = truncate(raw.path(), MAX_PATH);
        Integer start = nonNegative(raw.start());
        Integer end = nonNegative(raw.end());
        Integer blockIndex = nonNegative(raw.blockIndex());
        if (quote == null && sessionId == null && path == null && start == null && end == null && blockIndex == null) {
            return null;
        }
        return new HighlightRange(quote, start, end, blockIndex, sessionId, path);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static Integer nonNegative(Integer value) {
        return value != null && value >= 0 ? value : null;
    }

    /** null = client không gửi đoạn neo (giữ nguyên khi update, bỏ trống khi tạo mới). */
    private String encodeHighlight(HighlightRange request) {
        if (request == null) {
            return null;
        }
        HighlightRange clean = sanitizeHighlight(request);
        if (clean == null) {
            return CLEARED_HIGHLIGHT;
        }
        try {
            return HIGHLIGHT_MAPPER.writeValueAsString(clean);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("highlight không hợp lệ");
        }
    }

    private HighlightRange decodeHighlight(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            return sanitizeHighlight(HIGHLIGHT_READER.readValue(stored));
        } catch (Exception e) {
            return null;
        }
    }
    public record SearchHitResponse(String type, UUID id, String title, String excerpt) {
        static SearchHitResponse of(SearchHit hit) {
            return new SearchHitResponse(hit.type(), hit.id(), hit.title(), hit.excerpt());
        }
    }

    @GetMapping("/notes")
    public List<NoteResponse> list(@AuthenticationPrincipal UUID userId) {
        return notes.list(userId).stream().map(this::toResponse).toList();
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
        return toResponse(notes.get(userId, id));
    }

    @PostMapping("/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse create(@AuthenticationPrincipal UUID userId, @RequestBody NoteRequest request) {
        return toResponse(notes.create(userId, request.questionId(), request.moduleId(), request.noteType(),
                request.content(), encodeHighlight(request.highlight()), request.tags()));
    }

    @PutMapping("/notes/" + NOTE_ID)
    public NoteResponse update(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                               @RequestBody NoteRequest request) {
        return toResponse(notes.update(userId, id, request.questionId(), request.moduleId(), request.noteType(),
                request.content(), encodeHighlight(request.highlight()), request.tags()));
    }

    @DeleteMapping("/notes/" + NOTE_ID)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        notes.delete(userId, id);
    }

    @GetMapping("/search")
    public List<SearchHitResponse> search(@AuthenticationPrincipal UUID userId, @RequestParam String q) {
        return search.search(userId, q).stream().map(SearchHitResponse::of).toList();
    }

    @GetMapping("/users/me/bookmarks")
    public List<NoteResponse> bookmarks(@AuthenticationPrincipal UUID userId) {
        return notes.bookmarks(userId).stream().map(this::toResponse).toList();
    }

    @PostMapping("/notes/" + NOTE_ID + "/convert")
    public Map<String, UUID> convert(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return Map.of("cardId", notes.convertToSrsCard(userId, id));
    }
}
