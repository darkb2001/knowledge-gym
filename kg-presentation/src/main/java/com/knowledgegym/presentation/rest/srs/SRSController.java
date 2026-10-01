package com.knowledgegym.presentation.rest.srs;

import com.knowledgegym.learning.application.EnrollCardsUseCase;
import com.knowledgegym.learning.application.QueryDueUseCase;
import com.knowledgegym.learning.application.ReviewCardUseCase;
import com.knowledgegym.presentation.rest.srs.dto.EnrollRequest;
import com.knowledgegym.presentation.rest.srs.dto.ReviewRequest;
import com.knowledgegym.presentation.rest.srs.dto.SRSCardDTO;
import com.knowledgegym.presentation.rest.srs.dto.SrsResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * SRS/flashcard endpoints. Mọi thao tác đều **theo user đang đăng nhập** — `userId` lấy từ JWT
 * principal (đặt bởi {@code JwtAuthenticationFilter}), không bao giờ từ body/query: nhận userId từ
 * client cho phép đọc/ghi thẻ của người khác.
 *
 * <p>Authorization: chỉ cần authenticated (mọi role) — không `@PreAuthorize`, xem `SecurityConfig`
 * `anyRequest().authenticated()`.
 */
@RestController
@RequestMapping("/srs")
public class SRSController {

    private final EnrollCardsUseCase enrollCardsUseCase;
    private final QueryDueUseCase queryDueUseCase;
    private final ReviewCardUseCase reviewCardUseCase;

    public SRSController(EnrollCardsUseCase enrollCardsUseCase,
                         QueryDueUseCase queryDueUseCase,
                         ReviewCardUseCase reviewCardUseCase) {
        this.enrollCardsUseCase = enrollCardsUseCase;
        this.queryDueUseCase = queryDueUseCase;
        this.reviewCardUseCase = reviewCardUseCase;
    }

    /**
     * Enroll dual-mode. `moduleId` → enroll cả module (auto deck); `questionIds` → đúng list đó.
     * Gửi cả hai hoặc không gửi gì → 400 (use case ném `IllegalArgumentException`).
     *
     * 201 thay vì 200: request tạo tài nguyên mới (thẻ và/hoặc deck). Re-enroll trả `enrolled: 0`
     * chứ không phải lỗi — idempotent theo thiết kế.
     */
    @PostMapping("/enroll")
    @ResponseStatus(HttpStatus.CREATED)
    public SrsResponses.EnrollResponse enroll(@AuthenticationPrincipal UUID userId,
                                              @Valid @RequestBody EnrollRequest request) {
        return SrsResponses.EnrollResponse.of(enrollCardsUseCase.execute(userId,
                new EnrollCardsUseCase.EnrollCommand(request.moduleId(), request.questionIds(), request.deckId())));
    }

    /** Thẻ đến hạn, sắp theo `nextReview`; `moduleId` filter ở application (xem `QueryDueUseCase`). */
    @GetMapping("/due")
    public List<SRSCardDTO> due(@AuthenticationPrincipal UUID userId,
                                @RequestParam(required = false) UUID moduleId,
                                @RequestParam(required = false) Integer limit) {
        return queryDueUseCase.execute(userId, moduleId, limit).stream()
                .map(SRSCardDTO::of)
                .toList();
    }

    /**
     * Ghi kết quả 1 lần ôn: cập nhật thẻ + insert `study_attempts` trong cùng transaction
     * (xem `ReviewCardUseCase`).
     */
    @PostMapping("/review/{cardId}")
    public SrsResponses.ReviewResponse review(@AuthenticationPrincipal UUID userId,
                                              @PathVariable UUID cardId,
                                              @Valid @RequestBody ReviewRequest request) {
        ReviewCardUseCase.ReviewResult result = reviewCardUseCase.execute(
                userId, cardId, request.quality(), request.timeMs());
        return SrsResponses.ReviewResponse.of(cardId, result);
    }
}
