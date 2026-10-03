package com.knowledgegym.infrastructure.blog;

import com.knowledgegym.blog.domain.port.LearningDraftMaterializerPort;
import com.knowledgegym.content.application.SearchText;
import com.knowledgegym.content.domain.port.AnswerHtmlSanitizer;
import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.content.domain.port.QuestionOptionRepository;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.shared.application.ConflictException;
import com.knowledgegym.shared.application.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** One JDBC transaction for content, options, registry and audit; no JPA flush ordering dependency. */
@Component
public class JdbcLearningDraftMaterializerAdapter implements LearningDraftMaterializerPort {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final AnswerHtmlSanitizer sanitizer;

    public JdbcLearningDraftMaterializerAdapter(JdbcTemplate db, ObjectMapper json,
            QuestionRepository questions, QuestionOptionRepository options,
            ModuleRepository modules, AnswerHtmlSanitizer sanitizer) {
        this.db = db; this.json = json; this.sanitizer = sanitizer;
    }

    @Override @Transactional
    public UUID materialize(UUID draftId, UUID moduleId, UUID actor, String reason) {
        if (actor == null || reason == null || reason.isBlank() || reason.length() > 500)
            throw new IllegalArgumentException("actor/reason bắt buộc");
        var rows = db.queryForList("SELECT * FROM learning_content_drafts WHERE id=? FOR UPDATE", draftId);
        if (rows.isEmpty()) throw new NotFoundException("Draft không tồn tại");
        var draft = rows.getFirst();
        if (!"APPROVED".equals(draft.get("status"))) throw new ConflictException("Draft chưa được duyệt");
        // Serializes sort-order allocation and duplicate checks for all materializations in a module.
        if (db.queryForList("SELECT id FROM modules WHERE id=? AND is_active=true FOR UPDATE", moduleId).isEmpty())
            throw new NotFoundException("Module không tồn tại hoặc đã ẩn");
        if (Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM learning_draft_materializations WHERE draft_id=?)", Boolean.class, draftId)))
            throw new ConflictException("Draft đã được materialize");
        String kind = draft.get("kind").toString();
        JsonNode p;
        try { p = json.readTree(draft.get("payload").toString()); }
        catch (Exception e) { throw new IllegalArgumentException("Payload JSON không hợp lệ", e); }
        if (!p.isObject()) throw new IllegalArgumentException("Payload phải là object");
        String title = switch (kind) {
            case "FLASHCARD" -> text(p, "front");
            case "INTERVIEW" -> text(p, "question");
            default -> p.path("title").asText(draft.get("title").toString());
        };
        if (title.isBlank() || title.length() > 2000) throw new IllegalArgumentException("Title không hợp lệ");
        String answer = sanitizer.sanitize(text(p, switch (kind) {
            case "FLASHCARD" -> "backHtml"; case "INTERVIEW" -> "idealAnswerHtml"; default -> "answerHtml";
        }));
        if (SearchText.stripHtml(answer).isBlank()) throw new IllegalArgumentException("Answer rỗng sau sanitize");
        String difficulty = p.path("difficulty").asText("MID");
        if (!Set.of("JUNIOR", "MID", "SENIOR").contains(difficulty)) throw new IllegalArgumentException("Difficulty không hợp lệ");
        List<String> tags = new ArrayList<>();
        if (p.has("tags")) {
            if (!p.get("tags").isArray() || p.get("tags").size() > 30) throw new IllegalArgumentException("Tags không hợp lệ");
            for (var tag : p.get("tags")) {
                if (!tag.isTextual() || tag.asText().isBlank() || tag.asText().length() > 100) throw new IllegalArgumentException("Tag không hợp lệ");
                tags.add(tag.asText());
            }
        }
        record Option(String content, boolean correct) {}
        List<Option> options = new ArrayList<>();
        if (kind.equals("QUESTION")) {
            var nodes = p.path("options");
            if (!nodes.isArray() || nodes.size() < 2 || nodes.size() > 8) throw new IllegalArgumentException("Cần 2..8 options");
            Set<String> unique = new HashSet<>();
            for (var n : nodes) {
                String value = text(n, "content");
                if (value.isBlank() || value.length() > 2000 || !n.path("isCorrect").isBoolean() || !unique.add(normalize(value)))
                    throw new IllegalArgumentException("Option không hợp lệ hoặc trùng");
                options.add(new Option(value, n.path("isCorrect").asBoolean()));
            }
            if (options.stream().filter(Option::correct).count() != 1) throw new IllegalArgumentException("Cần đúng 1 đáp án đúng");
        }
        // Typed canonical JSON avoids delimiter collisions; option ordering does not change identity.
        String hash;
        try {
            String canonical = json.writeValueAsString(List.of(kind, normalize(title), normalize(answer),
                    options.stream().map(o -> List.of(normalize(o.content()), o.correct())).sorted(Comparator.comparing(Object::toString)).toList()));
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Content hash failure", e); }
        if (Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM learning_draft_materializations WHERE module_id=? AND content_hash=?)", Boolean.class, moduleId, hash)))
            throw new ConflictException("Nội dung đã tồn tại trong module");
        UUID id = UUID.randomUUID();
        int order = db.queryForObject("SELECT coalesce(max(sort_order),0)+1 FROM questions WHERE module_id=?", Integer.class, moduleId);
        String keywords = SearchText.build(title, SearchText.stripHtml(answer), String.join(" ", tags));
        db.update(c -> {
            var s = c.prepareStatement("INSERT INTO questions(id,module_id,title,answer_html,difficulty,tags,sort_order,searchable_text,content_status,options_manual) VALUES(?,?,?,?,?,?,?,?,'DRAFT',?)");
            s.setObject(1,id); s.setObject(2,moduleId); s.setString(3,title); s.setString(4,answer);
            s.setString(5,difficulty); s.setArray(6,c.createArrayOf("text",tags.toArray())); s.setInt(7,order);
            s.setString(8,keywords); s.setBoolean(9,kind.equals("QUESTION")); return s;
        });
        for (int i=0; i<options.size(); i++) {
            var o=options.get(i);
            db.update("INSERT INTO question_options(question_id,content,is_correct,display_order) VALUES(?,?,?,?)",id,o.content(),o.correct(),i);
        }
        db.update("INSERT INTO learning_draft_materializations(draft_id,module_id,question_id,kind,content_hash,created_by) VALUES(?,?,?,?,?,?)",draftId,moduleId,id,kind,hash,actor);
        db.update("INSERT INTO learning_draft_audit(draft_id,actor_id,action,details) VALUES(?,?,'MATERIALIZED_QUESTION',jsonb_build_object('questionId',?::text,'reason',?::text,'kind',?::text))",draftId,actor,id,reason.trim(),kind);
        return id;
    }
    @Override @Transactional
    public UUID rollback(UUID draftId, UUID actor, String reason) {
        if (actor == null || reason == null || reason.isBlank() || reason.length() > 500)
            throw new IllegalArgumentException("actor/reason bắt buộc");
        var rows = db.queryForList("SELECT question_id FROM learning_draft_materializations WHERE draft_id=? FOR UPDATE", draftId);
        if (rows.isEmpty()) throw new NotFoundException("Draft chưa được materialize");
        UUID id = (UUID) rows.getFirst().get("question_id");
        db.update("UPDATE questions SET content_status='HIDDEN',updated_at=now(),version=version+1 WHERE id=?", id);
        db.update("INSERT INTO learning_draft_audit(draft_id,actor_id,action,details) VALUES(?,?,'ROLLBACK_HIDE',jsonb_build_object('questionId',?::text,'reason',?::text))", draftId, actor, id, reason.trim());
        // Registry remains: rollback must not permit duplicate re-creation. History/FKs remain intact.
        return id;
    }

    private static String text(JsonNode node, String field) {
        if (!node.path(field).isTextual()) throw new IllegalArgumentException("Thiếu field: " + field);
        return node.path(field).asText().trim();
    }
    private static String normalize(String s) { return s.trim().replaceAll("\\s+", " "); }
}
