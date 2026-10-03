package com.knowledgegym.learning.domain.port;
import com.knowledgegym.learning.domain.model.*;
import java.util.*;
import java.time.Instant;
/** Lưu phiên + câu trả lời. Không còn chấm điểm nên không có API đọc điểm. */
public interface InterviewSessionRepository {
 InterviewSession save(InterviewSession session);
 Optional<InterviewSession> findByIdAndUserIdForUpdate(UUID id,UUID userId);
 List<InterviewSession> findByUserId(UUID userId,int page,int size);
 long countByUserId(UUID userId);
 void upsertAnswer(InterviewAnswer answer);
 void finish(UUID sessionId,Instant finishedAt);
}
