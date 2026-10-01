package com.knowledgegym.presentation.rest.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class InterviewRequests {

    private InterviewRequests() {
    }

    /** `mode` chỉ nhận `TEXT`: AUDIO cần `audio_url`/Garage (m11) nên use case từ chối bằng 400. */
    public record Start(@NotNull UUID topicId, @Min(1) @Max(20) int questionCount, @NotNull String mode) {
    }

    public record Answer(@NotNull UUID questionId, @NotBlank @Size(max = 20000) String userAnswer) {
    }
}
