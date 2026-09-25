package com.example.lms.repository;

import com.example.lms.entity.QuizAttemptEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuizAttemptRepository extends JpaRepository<QuizAttemptEntity, Long> {

    @EntityGraph(attributePaths = {"questionBank", "course", "learner"})
    Optional<QuizAttemptEntity> findFirstByQuestionBankIdAndLearnerIdAndStatusOrderByStartedAtDesc(
            Long questionBankId,
            Long learnerId,
            QuizAttemptEntity.Status status
    );

    Optional<QuizAttemptEntity> findTopByQuestionBankIdAndLearnerIdOrderByAttemptNumberDesc(
            Long questionBankId,
            Long learnerId
    );

    long countByQuestionBankIdAndLearnerId(Long questionBankId, Long learnerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"questionBank", "course", "learner"})
    @Query("SELECT a FROM QuizAttemptEntity a WHERE a.id = :attemptId")
    Optional<QuizAttemptEntity> findByIdForUpdate(@Param("attemptId") Long attemptId);

    @EntityGraph(attributePaths = {"questionBank", "course", "learner"})
    @Query("SELECT a FROM QuizAttemptEntity a WHERE a.id = :attemptId")
    Optional<QuizAttemptEntity> findWithReferencesById(@Param("attemptId") Long attemptId);
}
