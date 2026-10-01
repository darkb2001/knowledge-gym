package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.SrsCardJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataSrsCardRepository extends JpaRepository<SrsCardJpaEntity, UUID> {

    Optional<SrsCardJpaEntity> findByUserIdAndQuestionId(UUID userId, UUID questionId);

    /**
     * Đọc thẻ kèm khoá bi quan (`SELECT ... FOR UPDATE`) — tuần tự hoá hai lần review đồng thời
     * trên cùng thẻ (xem `SRSCardRepository.findByIdForUpdate`). Lọc luôn theo `userId` để không
     * khoá thẻ của người khác.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM SrsCardJpaEntity c WHERE c.id = :id AND c.userId = :userId")
    Optional<SrsCardJpaEntity> findByIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    /** Khớp composite index `idx_srs_due (user_id, next_review)` — V004. */
    List<SrsCardJpaEntity> findByUserIdAndNextReviewLessThanEqual(UUID userId, LocalDate date);

    long countByUserId(UUID userId);

    /**
     * Enroll idempotent: `ON CONFLICT (user_id, question_id) DO NOTHING` để re-enroll không reset
     * lịch ôn của thẻ đã có (UK `uk_srs_cards_user_question`, V004).
     *
     * `RETURNING id` trả **chỉ** các thẻ thực sự được chèn — caller phân biệt được "enroll mới"
     * với "đã có sẵn" mà không cần query thêm. Đây là native query ghi nhưng khai báo trả về
     * `List<UUID>`, nên **bắt buộc** chạy trong transaction (adapter bọc `@Transactional`).
     *
     * Danh sách question id truyền dạng literal `{uuid,...}` rồi cast — UUID chỉ chứa `[0-9a-f-]`
     * nên không cần quote từng phần tử, tránh phụ thuộc cách Hibernate bind mảng (cùng cách làm
     * với `tags` ở {@code SpringDataQuestionRepository.upsert}).
     */
    @Query(value = """
            INSERT INTO srs_cards
                (id, user_id, question_id, deck_id, interval_days, ease_factor, next_review, repetitions)
            SELECT gen_random_uuid(), cast(:userId as uuid), question, cast(:deckId as uuid),
                   0, :easeFactor, cast(:nextReview as date), 0
            FROM unnest(cast(:questionIds as uuid[])) AS question
            ON CONFLICT (user_id, question_id) DO NOTHING
            RETURNING id
            """, nativeQuery = true)
    List<UUID> insertIgnoringDuplicates(@Param("userId") UUID userId,
                                        @Param("deckId") UUID deckId,
                                        @Param("questionIds") String questionIdsLiteral,
                                        @Param("nextReview") LocalDate nextReview,
                                        @Param("easeFactor") double easeFactor);
}
