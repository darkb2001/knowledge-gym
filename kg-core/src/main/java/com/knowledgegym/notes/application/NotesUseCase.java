package com.knowledgegym.notes.application;

import com.knowledgegym.notes.domain.model.Note;
import com.knowledgegym.notes.domain.port.NoteRepository;
import com.knowledgegym.shared.application.NotFoundException;
import com.knowledgegym.shared.domain.model.NoteType;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class NotesUseCase {
    private final NoteRepository notes;

    public NotesUseCase(NoteRepository notes) {
        this.notes = notes;
    }

    public List<Note> list(UUID userId) {
        return notes.findByUser(userId);
    }

    public Note get(UUID userId, UUID noteId) {
        return notes.findOwned(userId, noteId)
                .orElseThrow(() -> new NotFoundException("Note not found"));
    }

    public Note create(UUID userId, UUID questionId, UUID moduleId, String noteType, String content,
                       String highlightRange, List<String> tags) {
        requirePublishedQuestion(questionId);
        Instant now = Instant.now();
        return notes.insert(new Note(
                UUID.randomUUID(),
                userId,
                questionId,
                moduleId,
                parseType(noteType),
                content,
                normalizeHighlight(highlightRange),
                copyTags(tags),
                now,
                now));
    }

    public Note update(UUID userId, UUID noteId, UUID questionId, UUID moduleId, String noteType, String content,
                       String highlightRange, List<String> tags) {
        Note existing = get(userId, noteId);
        if (Objects.equals(existing.questionId(), questionId)) {
            requireExistingQuestion(questionId);
        } else {
            requirePublishedQuestion(questionId);
        }
        // Client cũ không gửi `highlightRange`: giữ nguyên đoạn neo đã lưu thay vì xoá mất.
        String highlight = highlightRange == null ? existing.highlightRange() : normalizeHighlight(highlightRange);
        return notes.update(new Note(
                existing.id(),
                userId,
                questionId,
                moduleId,
                parseType(noteType),
                content,
                highlight,
                copyTags(tags),
                existing.createdAt(),
                Instant.now()));
    }

    public void delete(UUID userId, UUID noteId) {
        if (!notes.deleteOwned(userId, noteId)) {
            throw new NotFoundException("Note not found");
        }
    }

    public List<Note> bookmarks(UUID userId) {
        return notes.findBookmarks(userId);
    }

    public String exportMarkdown(UUID userId) {
        StringBuilder body = new StringBuilder("# My notes\n\n");
        for (Note note : list(userId)) {
            body.append("## ").append(note.noteType()).append("\n\n")
                    .append(Objects.toString(note.content(), "")).append("\n\n");
            if (!note.tags().isEmpty()) {
                body.append("Tags: ").append(String.join(", ", note.tags())).append("\n\n");
            }
        }
        return body.toString();
    }

    public UUID convertToSrsCard(UUID userId, UUID noteId) {
        Note note = get(userId, noteId);
        if (note.questionId() == null) {
            throw new IllegalArgumentException("Chỉ note gắn với question mới chuyển thành SRS card được");
        }
        requirePublishedQuestion(note.questionId());
        return notes.upsertSrsCardFromNote(userId, note.questionId(), noteId);
    }

    /** Đoạn neo do tầng presentation sinh ra (JSON) — chỉ nhận object gọn, chặn chuỗi rác/quá dài. */
    private static String normalizeHighlight(String highlightRange) {
        if (highlightRange == null || highlightRange.isBlank()) {
            return null;
        }
        String trimmed = highlightRange.trim();
        if (trimmed.length() > 2000) {
            throw new IllegalArgumentException("highlightRange quá dài");
        }
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException("highlightRange không hợp lệ");
        }
        // Object rỗng = người dùng bỏ neo; lưu NULL để không còn neo nào trên trang đọc.
        return trimmed.equals("{}") ? null : trimmed;
    }

    private static List<String> copyTags(List<String> tags) {
        if (tags == null) return List.of();
        if (tags.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("tags không được chứa null");
        }
        return List.copyOf(tags);
    }

    private void requirePublishedQuestion(UUID questionId) {
        if (questionId != null && !notes.publishedQuestionExists(questionId)) {
            throw new IllegalArgumentException("questionId không khả dụng");
        }
    }

    private void requireExistingQuestion(UUID questionId) {
        if (questionId == null) {
            return;
        }
        if (!notes.questionExists(questionId)) {
            throw new IllegalArgumentException("questionId không hợp lệ");
        }
    }

    private static String parseType(String raw) {
        try {
            return NoteType.valueOf(Objects.requireNonNull(raw).trim().toUpperCase(Locale.ROOT)).name();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("noteType không hợp lệ");
        }
    }
}
