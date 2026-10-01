package com.knowledgegym.learning.application.strategy;

import com.knowledgegym.learning.domain.model.QuizStrategy;
import com.knowledgegym.shared.domain.model.Difficulty;

import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Chọn câu cho phiên quiz theo một chiến lược.
 *
 * <p><b>Hợp đồng:</b> {@link #rank} trả **xếp hạng đầy đủ** của pool ứng viên trong module (ưu tiên
 * giảm dần), không phải "N câu đã chọn". Use case cắt top-N ở cuối, nhờ vậy:
 * <ul>
 *   <li>Mọi strategy có cùng ngữ nghĩa "câu nào đáng hỏi trước" và cut nhất quán.</li>
 *   <li>Strategy trả rỗng (không có tín hiệu — vd `WEAKNESS` khi user chưa sai câu nào) không làm
 *       hỏng endpoint: use case vẫn còn pool để hỏi.</li>
 *   <li>Test assert được **thứ tự** chứ không chỉ tập id, nên bắt được strategy xếp sai ưu tiên.</li>
 * </ul>
 *
 * <p>Cài đặt nằm ở `application/strategy` (không phải domain) vì chúng gọi repository — domain chỉ
 * giữ tri thức thuần (`Sm2Scheduler`, {@code RandomOrder}).
 *
 * <p>Đăng ký vào `Map<QuizStrategy, QuizGenerationStrategy>` ở `UseCaseConfig`; `kg-core` không có
 * Spring nên không dùng `@Component`/`@Qualifier`.
 */
public interface QuizGenerationStrategy {

    QuizStrategy strategy();

    /**
     * @param moduleId   module đang hỏi — mọi id trả về phải thuộc module này
     * @param difficulty filter tuỳ chọn (client gửi); null = mọi độ khó
     * @param random     nguồn ngẫu nhiên do caller truyền để test tái lập được
     * @return id câu đã xếp hạng (không trùng); có thể rỗng nếu không có tín hiệu riêng của strategy
     */
    List<UUID> rank(UUID userId, UUID moduleId, Difficulty difficulty, RandomGenerator random);
}
