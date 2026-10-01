package com.knowledgegym.learning.domain.port;

import com.knowledgegym.learning.domain.model.SrsDeck;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port cho `srs_decks`. */
public interface SrsDeckRepository {

    SrsDeck save(SrsDeck deck);

    /**
     * Deck auto của user cho 1 module. V004 **không** có UK `(user_id, module_id)` → đây là guard
     * duy nhất chống tạo deck trùng mỗi lần enroll; xem `EnrollCardsUseCase`.
     */
    Optional<SrsDeck> findByUserIdAndModuleId(UUID userId, UUID moduleId);

    /**
     * Deck theo id, giới hạn trong chủ sở hữu. Dùng khi enroll mode B kèm `deckId`: load cả danh
     * sách deck rồi lọc trong bộ nhớ là lãng phí và che mất ý định "tra 1 deck của tôi".
     */
    Optional<SrsDeck> findByIdAndUserId(UUID id, UUID userId);

    List<SrsDeck> findByUserId(UUID userId);
}
