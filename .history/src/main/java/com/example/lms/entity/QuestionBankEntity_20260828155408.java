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

import java.time.LocalDateTime;

/**
 * Question Bank is deliberately one aggregate/entity. Sections, questions,
 * options, and import metadata are stored in contentJson and are only changed
 * through QuestionBankService. This keeps ownership, copying, orphan cleanup,
 * and optimistic locking atomic without introducing child entities.
 */
@Entity
@Table(
        name = "question_banks",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_question_bank_creator_idempotency",
                columnNames = {"created_by_user_id", "idempotency_key"}
        ),
        indexes = {
                @Index(name = "idx_question_banks_name", columnList = "name"),
                @Index(name = "idx_question_banks_creator", columnList = "created_by_user_id"),
                @Index(name = "idx_question_banks_course", columnList = "course_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class QuestionBankEntity {

    private static final String EMPTY_CONTENT =
            "{\"sections\":[],\"questions\":[],\"imports\":[]}";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", foreignKey = @ForeignKey(name = "fk_question_bank_course"))
    private CourseEntity course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "created_by_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_question_bank_creator")
    )
    private User createdBy;

    @Lob
    @Column(name = "content_json", nullable = false, columnDefinition = "TEXT")
    private String contentJson = EMPTY_CONTENT;

    @Column(name = "idempotency_key", length = 200)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        if (this.contentJson == null || this.contentJson.isBlank()) {
            this.contentJson = EMPTY_CONTENT;
        }
        if (this.version == null) {
            this.version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
