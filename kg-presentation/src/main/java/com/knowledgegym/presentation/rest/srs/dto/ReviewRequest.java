package com.knowledgegym.presentation.rest.srs.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body của `POST /srs/review/{cardId}`.
 *
 * <p>`quality` bắt buộc + biên 0–3 validate ngay ở tầng HTTP (fail fast, message rõ field);
 * use case vẫn kiểm tra lại vì nó là API dùng chung cho caller không qua HTTP.
 *
 * <p>`timeMs` optional: FE **phải** gửi (đo từ lúc lật card), nhưng BE không bắt buộc — client
 * khác (vd CLI test) không đo được. Lưu NULL chứ không default 0, xem `ReviewCardUseCase`.
 */
public record ReviewRequest(@NotNull @Min(0) @Max(3) Integer quality, @Min(0) Integer timeMs) {
}
