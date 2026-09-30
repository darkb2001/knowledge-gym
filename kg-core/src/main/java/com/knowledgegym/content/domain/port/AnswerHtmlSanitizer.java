package com.knowledgegym.content.domain.port;

/**
 * Làm sạch HTML câu trả lời trước khi persist.
 *
 * Port ở kg-core (không phải utility trong infrastructure) vì **cả hai** đường ghi đều phải đi qua:
 * import từ docs/ (JsoupContentSource) và admin tạo/sửa tay (ManageQuestionsUseCase).
 * Nếu chỉ sanitize ở import, admin có thể vô tình (hoặc cố ý) lưu `<script>` rồi nó được
 * trả nguyên văn ở `GET /questions/{id}` — mất tính bất biến của `answer_html`.
 *
 * Đây là invariant của domain, nên nằm ở tầng domain, không phải ở controller.
 */
public interface AnswerHtmlSanitizer {

    /**
     * @param rawHtml HTML thô từ bất kỳ nguồn nào.
     * @return HTML đã lọc theo whitelist (bỏ {@code script}, {@code iframe}, thuộc tính sự kiện
     *         và URL {@code javascript:}), hoặc chuỗi rỗng.
     */
    String sanitize(String rawHtml);
}
