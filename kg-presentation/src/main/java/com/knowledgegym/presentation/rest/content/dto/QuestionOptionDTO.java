package com.knowledgegym.presentation.rest.content.dto;

import java.util.UUID;

/** Lựa chọn trả lời ở API public — **không có** cờ đáp án đúng (xem D5 trong plan m4). */
public record QuestionOptionDTO(UUID id, String content, int displayOrder) {
}
