package com.knowledgegym.presentation.rest.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class InterviewRequests {

    private InterviewRequests() {
    }

    /** `mode` chỉ nhận `TEXT`: AUDIO cần `audio_url`/Garage (m11) nên use case từ chối bằng 400. */
    public record Start(@NotNull UUID topicId, @Min(1) @Max(20) int questionCount, @NotNull String mode) {
    }

    /** Submit toàn cục: FE gửi 1 lần cho mọi câu; câu bỏ trống gửi `answer` rỗng/không gửi. */
    public record Submit(@NotNull @Size(max = 100) List<Item> answers) {

        public record Item(@NotNull UUID questionId, @Size(max = 20000) String answer) {
        }
    }
}
