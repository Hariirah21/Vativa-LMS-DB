package com.example.lms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
        name = "quiz_attempt_answers",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_quiz_attempt_answer",
                columnNames = {"attempt_id", "question_id"}
        ),
        indexes = @Index(name = "idx_quiz_attempt_answer_attempt", columnList = "attempt_id")
)
@Getter
@Setter
@NoArgsConstructor
public class QuizAttemptAnswerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_quiz_attempt_answer_attempt"))
    private QuizAttemptEntity attempt;

    @Column(name = "question_id", nullable = false, length = 100)
    private String questionId;

    @Lob
    @Column(name = "selected_option_ids_json", nullable = false, columnDefinition = "TEXT")
    private String selectedOptionIdsJson = "[]";

    @Column(name = "text_answer", length = 500)
    private String textAnswer;

    @Column(name = "is_correct")
    private Boolean correct;

    @Column(name = "awarded_points", precision = 12, scale = 2)
    private BigDecimal awardedPoints;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
