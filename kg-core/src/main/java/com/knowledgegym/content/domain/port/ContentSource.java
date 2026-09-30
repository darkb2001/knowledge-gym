package com.knowledgegym.content.domain.port;

import com.knowledgegym.content.domain.model.ContentCatalog;

/**
 * Port đọc nội dung câu hỏi từ nguồn ngoài (hiện tại: docs/*.html qua Jsoup).
 * Trả về catalog đã chuẩn hoá; lỗi IO/parse được adapter bọc thành unchecked exception.
 */
public interface ContentSource {

    /**
     * @param docsPath thư mục chứa các file HTML (`index.html` + module files).
     * @return topics, modules, questions đọc được — đã sanitize HTML câu trả lời.
     */
    ContentCatalog readCatalog(String docsPath);
}
