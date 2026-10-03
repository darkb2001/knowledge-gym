package com.knowledgegym.learning.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Câu trả lời của user trong 1 phiên mock interview.
 * Không còn chấm điểm: sau khi submit, client hiển thị `answerHtml` (đáp án mẫu soạn sẵn)
 * để người dùng tự đối chiếu.
 */
public record InterviewAnswer(UUID sessionId, UUID questionId, String userAnswer, String answerHtml,
                              Instant attemptedAt) {}
