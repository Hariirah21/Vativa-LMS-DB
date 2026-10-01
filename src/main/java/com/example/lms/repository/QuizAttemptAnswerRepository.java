package com.example.lms.repository;

import com.example.lms.entity.QuizAttemptAnswerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizAttemptAnswerRepository
        extends JpaRepository<QuizAttemptAnswerEntity, Long> {

    Optional<QuizAttemptAnswerEntity> findByAttemptIdAndQuestionId(
            Long attemptId,
            String questionId
    );

    List<QuizAttemptAnswerEntity> findAllByAttemptIdOrderByIdAsc(Long attemptId);
}
