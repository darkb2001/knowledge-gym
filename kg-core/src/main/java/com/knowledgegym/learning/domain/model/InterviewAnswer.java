package com.knowledgegym.learning.domain.model;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record InterviewAnswer(UUID sessionId,UUID questionId,String userAnswer,BigDecimal keywordScore,String feedback,String sampleAnswer,Instant attemptedAt) {}
