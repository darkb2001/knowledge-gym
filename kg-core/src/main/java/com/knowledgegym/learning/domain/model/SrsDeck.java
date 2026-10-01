package com.knowledgegym.learning.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Deck ôn tập. Hai loại, phân biệt bằng {@code moduleId}/{@code isCustom}:
 * <ul>
 *   <li><b>Deck theo module</b> — {@code moduleId != null}, {@code isCustom = false}. Tự tạo khi
 *       enroll bulk từ 1 module; mỗi user chỉ có 1 deck/module (guard ở application layer, không
 *       có UK ở DB nên domain là nơi duy nhất giữ invariant này).</li>
 *   <li><b>Deck tuỳ chỉnh</b> — {@code moduleId == null}, {@code isCustom = true}. Tạo từ danh sách
 *       question cụ thể (bookmark → deck wire ở m08).</li>
 * </ul>
 *
 * <p><b>Invariant:</b> {@code isCustom == (moduleId == null)}. Bảng `srs_decks` (V004) không có
 * CHECK constraint cho quan hệ này, và m5 không thêm migration — nên factory ở đây là hàng rào duy
 * nhất. Cho phép row lệch (vd deck module nhưng is_custom=true) sẽ làm UI phân loại sai và không
 * có gì phát hiện về sau.
 */
public class SrsDeck {

    private UUID id;
    private UUID userId;
    private String name;
    private UUID moduleId;
    private boolean custom;

    private SrsDeck() {
    }

    /** Deck theo module — dùng cho enroll bulk mode A. */
    public static SrsDeck forModule(UUID userId, UUID moduleId, String name) {
        Objects.requireNonNull(moduleId, "moduleId — deck theo module phải có module");
        SrsDeck deck = new SrsDeck();
        deck.userId = Objects.requireNonNull(userId, "userId");
        deck.moduleId = moduleId;
        deck.name = requireName(name);
        deck.custom = false;
        return deck;
    }

    /** Deck tuỳ chỉnh — {@code moduleId} luôn null theo invariant. */
    public static SrsDeck custom(UUID userId, String name) {
        SrsDeck deck = new SrsDeck();
        deck.userId = Objects.requireNonNull(userId, "userId");
        deck.moduleId = null;
        deck.name = requireName(name);
        deck.custom = true;
        return deck;
    }

    /**
     * Dựng lại deck từ DB. Validate invariant ở đây để row lệch (dữ liệu cũ, sửa tay) nổ ngay khi
     * đọc thay vì âm thầm lan vào luồng enroll.
     */
    public static SrsDeck rehydrate(UUID id, UUID userId, String name, UUID moduleId, boolean custom) {
        SrsDeck deck = new SrsDeck();
        deck.id = Objects.requireNonNull(id, "id");
        deck.userId = Objects.requireNonNull(userId, "userId");
        deck.name = requireName(name);
        deck.moduleId = moduleId;
        deck.custom = custom;
        deck.assertInvariant();
        return deck;
    }

    private void assertInvariant() {
        if (custom != (moduleId == null)) {
            throw new IllegalStateException(
                    "srs_decks invariant violated: is_custom=" + custom + " nhưng moduleId=" + moduleId);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public UUID getModuleId() { return moduleId; }
    public boolean isCustom() { return custom; }

    /** {@code true} khi deck gắn 1 module cụ thể (enroll bulk/due theo module). */
    public boolean isForModule() {
        return moduleId != null;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tên deck không được để trống");
        }
        return name.trim();
    }
}
