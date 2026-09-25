package com.example.lms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "quiz_attempts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_quiz_attempt_number",
                columnNames = {"question_bank_id", "learner_id", "attempt_number"}
        ),
        indexes = {
                @Index(name = "idx_quiz_attempt_learner_quiz_status",
                        columnList = "learner_id, question_bank_id, status"),
                @Index(name = "idx_quiz_attempt_submitted", columnList = "submitted_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class QuizAttemptEntity {

    public enum Status {
        IN_PROGRESS,
        COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_quiz_attempt_learner"))
    private User learner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_bank_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_quiz_attempt_question_bank"))
    private QuestionBankEntity questionBank;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_quiz_attempt_course"))
    private CourseEntity course;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "total_questions", nullable = false)
    private Integer totalQuestions;

    @Column(name = "correct_answers")
    private Integer correctAnswers;

    @Column(name = "earned_points", precision = 12, scale = 2)
    private BigDecimal earnedPoints;

    @Column(name = "possible_points", precision = 12, scale = 2)
    private BigDecimal possiblePoints;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "pass_percentage", precision = 5, scale = 2)
    private BigDecimal passPercentage;

    @Column
    private Boolean passed;

    @Column(name = "time_spent_seconds")
    private Long timeSpentSeconds;

    @Lob
    @Column(name = "question_snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String questionSnapshotJson;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}
