package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.infrastructure.persistence.entity.StudyAttemptJpaEntity;
import com.knowledgegym.infrastructure.persistence.repository.SpringDataStudyAttemptRepository;
import com.knowledgegym.learning.domain.model.AttemptSource;
import com.knowledgegym.learning.domain.model.StudyAttempt;
import com.knowledgegym.learning.domain.port.StudyAttemptRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class StudyAttemptRepositoryAdapter implements StudyAttemptRepository {

    private final SpringDataStudyAttemptRepository springData;

    public StudyAttemptRepositoryAdapter(SpringDataStudyAttemptRepository springData) {
        this.springData = springData;
    }

    @Override
    public StudyAttempt save(StudyAttempt attempt) {
        StudyAttemptJpaEntity entity = new StudyAttemptJpaEntity();
        entity.setId(attempt.getId() != null ? attempt.getId() : UUID.randomUUID());
        entity.setUserId(attempt.getUserId());
        entity.setQuestionId(attempt.getQuestionId());
        entity.setSource(attempt.getSource().name());
        entity.setAnswer(attempt.getAnswer());
        entity.setCorrect(attempt.isCorrect());
        entity.setScore(attempt.getScore());
        entity.setTimeMs(attempt.getTimeMs());
        entity.setAttemptedAt(attempt.getAttemptedAt());
        return toDomain(springData.save(entity));
    }

    static StudyAttempt toDomain(StudyAttemptJpaEntity entity) {
        StudyAttempt attempt = StudyAttempt.record(
                entity.getUserId(), entity.getQuestionId(),
                AttemptSource.valueOf(entity.getSource()),
                entity.isCorrect(), entity.getTimeMs(), entity.getAttemptedAt());
        attempt.setId(entity.getId());
        attempt.setAnswer(entity.getAnswer());
        attempt.setScore(entity.getScore());
        return attempt;
    }
}
