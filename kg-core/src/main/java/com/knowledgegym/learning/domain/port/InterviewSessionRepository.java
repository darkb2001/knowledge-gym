package com.knowledgegym.learning.domain.port;
import com.knowledgegym.learning.domain.model.*;
import java.util.*;
import java.time.Instant;
/** Lưu phiên + câu trả lời. Không còn chấm điểm nên không có API đọc điểm. */
public interface InterviewSessionRepository {
 InterviewSession save(InterviewSession session);
 Optional<InterviewSession> findByIdAndUserIdForUpdate(UUID id,UUID userId);
 Optional<InterviewSession> findByIdAndUserId(UUID id,UUID userId);
 /** Câu đã trả lời (không tính row placeholder) theo questionId — dựng trang kết quả trong 1 query. */
 Map<UUID, InterviewAnswer> answersOf(UUID sessionId);
 List<InterviewSession> findByUserId(UUID userId,int page,int size);
 long countByUserId(UUID userId);
 void upsertAnswer(InterviewAnswer answer);
 void finish(UUID sessionId,Instant finishedAt);
}
