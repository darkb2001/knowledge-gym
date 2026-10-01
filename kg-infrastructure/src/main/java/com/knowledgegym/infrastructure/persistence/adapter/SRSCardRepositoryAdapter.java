package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.infrastructure.persistence.entity.SrsCardJpaEntity;
import com.knowledgegym.infrastructure.persistence.repository.SpringDataSrsCardRepository;
import com.knowledgegym.learning.domain.model.SRSCard;
import com.knowledgegym.learning.domain.port.SRSCardRepository;
import com.knowledgegym.learning.domain.service.Sm2Scheduler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class SRSCardRepositoryAdapter implements SRSCardRepository {

    private final SpringDataSrsCardRepository springData;

    public SRSCardRepositoryAdapter(SpringDataSrsCardRepository springData) {
        this.springData = springData;
    }

    @Override
    public Optional<SRSCard> findById(UUID id) {
        return springData.findById(id).map(SRSCardRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<SRSCard> findByIdForUpdate(UUID id, UUID userId) {
        return springData.findByIdForUpdate(id, userId).map(SRSCardRepositoryAdapter::toDomain);
    }

    @Override
    public List<SRSCard> findDue(UUID userId, LocalDate today) {
        return springData.findByUserIdAndNextReviewLessThanEqual(userId, today).stream()
                .map(SRSCardRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public SRSCard save(SRSCard card) {
        SrsCardJpaEntity entity = card.getId() == null
                ? new SrsCardJpaEntity()
                : springData.findById(card.getId()).orElseGet(SrsCardJpaEntity::new);
        if (entity.getId() == null) {
            entity.setId(card.getId() != null ? card.getId() : UUID.randomUUID());
        }
        entity.setUserId(card.getUserId());
        entity.setQuestionId(card.getQuestionId());
        entity.setDeckId(card.getDeckId());
        entity.setSourceNoteId(card.getSourceNoteId());
        entity.setIntervalDays(card.getIntervalDays());
        entity.setEaseFactor(BigDecimal.valueOf(card.getEaseFactor()));
        entity.setNextReview(card.getNextReview());
        entity.setRepetitions(card.getRepetitions());
        entity.setLastReviewedAt(card.getLastReviewedAt());
        return toDomain(springData.save(entity));
    }

    @Override
    public long countByUserId(UUID userId) {
        return springData.countByUserId(userId);
    }

    /**
     * `ON CONFLICT ... DO NOTHING` + `RETURNING id` cần transaction đang mở: câu lệnh ghi nhưng
     * khai báo trả về danh sách (Hibernate chạy qua đường query, không phải update).
     */
    @Override
    @Transactional
    public List<UUID> insertIgnoringDuplicates(UUID userId, UUID deckId, Collection<UUID> questionIds,
                                               LocalDate nextReview) {
        if (questionIds == null || questionIds.isEmpty()) {
            return List.of();
        }
        // Ease mặc định lấy từ domain service thay vì literal `2.50` trong SQL: đổi hằng số ở
        // `Sm2Scheduler` mà quên chỗ này sẽ tạo thẻ mới lệch ease so với mọi thẻ khác.
        return springData.insertIgnoringDuplicates(userId, deckId, toPgUuidArrayLiteral(questionIds),
                nextReview, Sm2Scheduler.DEFAULT_EASE_FACTOR);
    }

    static SRSCard toDomain(SrsCardJpaEntity entity) {
        SRSCard card = SRSCard.newCard(entity.getUserId(), entity.getQuestionId(),
                entity.getDeckId(), entity.getSourceNoteId(), entity.getNextReview());
        card.setId(entity.getId());
        card.setEaseFactor(entity.getEaseFactor().doubleValue());
        card.setIntervalDays(entity.getIntervalDays());
        card.setRepetitions(entity.getRepetitions());
        card.setLastReviewedAt(entity.getLastReviewedAt());
        return card;
    }

    /**
     * Literal `{uuid,uuid}` cho native query. UUID chỉ chứa `[0-9a-f-]` nên an toàn trong literal;
     * vẫn lọc ký tự lạ như một lớp phòng thủ (cùng cách với `tags` ở `QuestionRepositoryAdapter`).
     */
    static String toPgUuidArrayLiteral(Collection<UUID> values) {
        return values.stream()
                .map(UUID::toString)
                .map(value -> value.replaceAll("[^0-9a-fA-F-]", ""))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining(",", "{", "}"));
    }
}
