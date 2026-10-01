package com.knowledgegym.content.domain.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.QuestionQuery;
import com.knowledgegym.shared.domain.model.PageResult;

public interface QuestionRepository {

    Optional<Question> findById(UUID id);

    /**
     * Nhiều câu theo id trong 1 query. Thêm cho m5 (phiên flashcard cần nội dung của cả list thẻ
     * đến hạn) — gọi {@link #findById} trong vòng lặp là N+1, và N ở đây là số thẻ trong phiên
     * (tới 100) nên không thể coi là chi phí không đáng kể.
     *
     * Trả về **chỉ những câu tìm thấy** (không có phần tử null): câu đã bị re-import xoá là chuyện
     * bình thường, caller tự quyết định bỏ qua hay báo lỗi.
     */
    List<Question> findByIds(Collection<UUID> ids);

    /**
     * Tra theo natural key của import. Dùng để phát hiện `sortOrder` đã bị chiếm trước khi
     * ghi — cho phép trả 409 thay vì upsert ghi đè im lặng.
     */
    Optional<Question> findByModuleIdAndSortOrder(UUID moduleId, int sortOrder);

    PageResult<Question> search(QuestionQuery query);

    /** Insert mới hoặc update theo natural key `(moduleId, sortOrder)`. @return số dòng ghi. */
    int saveOrUpdateByNaturalKey(List<Question> questions);

    /**
     * Xoá câu hỏi trong {@code moduleId} mà {@code sortOrder} không còn trong tập keep
     * sau lần parse — docs là source of truth khi re-import.
     *
     * {@code keepSortOrders} rỗng → xoá toàn bộ câu của module.
     *
     * @return số dòng đã xoá
     */
    int deleteAbsentSortOrders(UUID moduleId, Collection<Integer> keepSortOrders);

    Question save(Question question);

    void deleteById(UUID id);

    /** sort_order kế tiếp trong module — dùng khi admin tạo câu mới. */
    int nextSortOrder(UUID moduleId);
}
