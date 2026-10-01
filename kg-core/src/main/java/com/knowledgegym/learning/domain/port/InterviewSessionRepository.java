package com.knowledgegym.learning.domain.port;
import com.knowledgegym.learning.domain.model.*;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
public interface InterviewSessionRepository {
 InterviewSession save(InterviewSession session);
 Optional<InterviewSession> findByIdAndUserIdForUpdate(UUID id,UUID userId);
 List<InterviewSession> findByUserId(UUID userId,int page,int size);
 long countByUserId(UUID userId);
 void upsertAnswer(InterviewAnswer answer);
 List<InterviewAnswer> findSubmittedAnswers(UUID sessionId);
 void finish(UUID sessionId,BigDecimal score,Instant finishedAt);
}
