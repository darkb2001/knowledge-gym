package com.knowledgegym.shared.domain.model;

import java.util.List;

/**
 * Kết quả phân trang ở tầng domain — không phụ thuộc Spring Data `Page`.
 * Presentation map sang JSON (`items` + metadata) để không rò rỉ shape của Spring ra ngoài.
 */
public record PageResult<T>(List<T> items, int page, int size, long totalElements) {

    public int totalPages() {
        return size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
