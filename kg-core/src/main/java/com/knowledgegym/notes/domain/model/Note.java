package com.knowledgegym.notes.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ghi chú của người dùng.
 *
 * <p>{@code highlightRange} lưu JSON mô tả đoạn văn người dùng bôi đen khi ghi chú (câu trích
 * nguyên văn + chỉ số khối + phiên đọc). Nhờ vậy trang khám phá hiển thị lại đúng vị trí đã
 * đánh dấu, và mỗi người đọc giữ tập ghi chú riêng. Tầng presentation kiểm tra/chuẩn hoá chuỗi
 * này trước khi truyền xuống; ở đây chỉ lưu trữ.
 */
public record Note(
        UUID id,
        UUID userId,
        UUID questionId,
        UUID moduleId,
        String noteType,
        String content,
        String highlightRange,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt
) {}
