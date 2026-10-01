package com.knowledgegym.learning.domain.port;

import com.knowledgegym.learning.domain.model.SRSCard;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port cho `srs_cards`. Không extends JpaRepository — implementation ở infrastructure. */
public interface SRSCardRepository {

    Optional<SRSCard> findById(UUID id);

    /**
     * Như {@link #findById} nhưng khoá row bằng `SELECT ... FOR UPDATE`, dùng trong luồng đọc–sửa–ghi
     * của `ReviewCardUseCase`.
     *
     * <p>Lý do: `srs_cards` không có cột `version` (V004) nên hai request review đồng thời cùng đọc
     * một trạng thái rồi cùng ghi sẽ mất một lần cập nhật lịch (last-write-wins) dù cả hai đều tạo
     * attempt — lịch ôn và số lần ôn lệch nhau. Khoá bi quan làm hai lần review trên cùng thẻ nối
     * tiếp nhau; chỉ áp dụng cho một row nên không đụng tới luồng đọc khác.
     *
     * <p>Kèm {@code userId} để không khoá thẻ của người khác: id thẻ là UUID khó đoán, nhưng giới
     * hạn khoá theo chủ sở hữu là hàng rào đúng và miễn phí.
     *
     * <p>Chỉ có hiệu lực khi gọi trong transaction (adapter dựa vào tx của use case).
     */
    Optional<SRSCard> findByIdForUpdate(UUID id, UUID userId);

    /** `next_review <= today`, dùng composite index `idx_srs_due (user_id, next_review)` (V004). */
    List<SRSCard> findDue(UUID userId, LocalDate today);

    SRSCard save(SRSCard card);

    /** Số thẻ của user — hiển thị tiến độ deck. */
    long countByUserId(UUID userId);

    /**
     * Bulk insert idempotent theo UK `(user_id, question_id)` — thẻ đã tồn tại bị bỏ qua thay vì
     * ghi đè (ghi đè sẽ reset lịch ôn của người học).
     *
     * @return id của **các thẻ mới** được tạo; rỗng nếu tất cả đã tồn tại
     */
    List<UUID> insertIgnoringDuplicates(UUID userId, UUID deckId, Collection<UUID> questionIds,
                                        LocalDate nextReview);
}
