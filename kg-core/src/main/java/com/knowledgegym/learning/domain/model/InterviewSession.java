package com.knowledgegym.learning.domain.model;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
public record InterviewSession(UUID id,UUID userId,UUID topicId,int questionCount,String mode,String status,BigDecimal overallScore,Instant startedAt,Instant finishedAt,List<UUID> questionIds) {
 public InterviewSession {questionIds=List.copyOf(questionIds);}
}
