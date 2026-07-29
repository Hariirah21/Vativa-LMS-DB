package com.example.lms.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "sections",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_section_name_per_course", columnNames = {"course_id", "name"})
    },
    indexes = {
        @Index(name = "idx_section_course_id", columnList = "course_id")
    }
)
public class SectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, foreignKey = @ForeignKey(name = "fk_section_course"))
    private CourseEntity course;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(name = "section_order", nullable = false)
    private Integer sectionOrder;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public SectionEntity() {
    }

    private SectionEntity(Builder b) {
        this.id = b.id;
        this.course = b.course;
        this.name = b.name;
        this.sectionOrder = b.sectionOrder;
        this.createdAt = b.createdAt;
        this.updatedAt = b.updatedAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ---------- getters & setters ----------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CourseEntity getCourse() {
        return course;
    }

    public void setCourse(CourseEntity course) {
        this.course = course;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSectionOrder() {
        return sectionOrder;
    }

    public void setSectionOrder(Integer sectionOrder) {
        this.sectionOrder = sectionOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ---------- builder ----------

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private CourseEntity course;
        private String name;
        private Integer sectionOrder;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder course(CourseEntity course) {
            this.course = course;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder sectionOrder(Integer sectionOrder) {
            this.sectionOrder = sectionOrder;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public SectionEntity build() {
            return new SectionEntity(this);
        }
    }
}
