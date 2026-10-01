package com.knowledgegym.presentation.rest.srs.dto;

import java.util.List;
import java.util.UUID;

/**
 * Body của `POST /srs/enroll` — dual-mode (xem `EnrollCardsUseCase`).
 *
 * <p>Không ràng buộc "đúng một trong hai" bằng bean validation vì hai field này **loại trừ nhau
 * nhưng không bắt buộc cùng lúc** — dạng `@AssertTrue` sẽ tạo message khó đọc, và rule là tri
 * thức nghiệp vụ thuộc use case. Use case ném `IllegalArgumentException` → 400 qua
 * `GlobalExceptionHandler`.
 */
public record EnrollRequest(UUID moduleId, List<UUID> questionIds, UUID deckId) {
}
