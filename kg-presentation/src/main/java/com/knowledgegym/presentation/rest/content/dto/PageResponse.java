package com.knowledgegym.presentation.rest.content.dto;

import com.knowledgegym.shared.domain.model.PageResult;

import java.util.List;
import java.util.function.Function;

/**
 * Shape phân trang ổn định cho API — không lộ `Page` của Spring Data (nếu sau này đổi
 * implementation thì client không phải sửa).
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PageResponse<T> of(PageResult<S> result, Function<S, T> mapper) {
        return new PageResponse<>(result.items().stream().map(mapper).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}
