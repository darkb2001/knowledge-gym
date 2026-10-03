package com.knowledgegym.learning.domain.model;
import java.time.Instant;
import java.util.*;
/** Phiên mock interview — không còn điểm tổng (cột `overall_score` bỏ ở V035). */
public record InterviewSession(UUID id,UUID userId,UUID topicId,int questionCount,String mode,String status,Instant startedAt,Instant finishedAt,List<UUID> questionIds) {
 public InterviewSession {questionIds=List.copyOf(questionIds);}
}
