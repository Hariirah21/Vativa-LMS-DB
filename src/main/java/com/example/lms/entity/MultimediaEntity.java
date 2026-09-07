package com.example.lms.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Entity
@Table(
        name = "multimedia",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_multimedia_client_request",
                columnNames = "client_request_id"
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultimediaEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ValidResourceName
    @Column(name = "resource_name", nullable = false, length = 100)
    private String resourceName;

    @Column(name = "resource_description", length = 500)
    private String resourceDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 20)
    @ColumnDefault("'OTHER'")
    @Builder.Default
    private MultimediaResourceType resourceType = MultimediaResourceType.OTHER;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "content_type", length = 150)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private CourseEntity course;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "uploaded_by", nullable = false, length = 255)
    private String uploadedBy;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "client_request_id")
    private UUID clientRequestId;

    @PrePersist
    protected void onCreate() {
        normalizeResourceName();
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        normalizeResourceName();
    }

    private void normalizeResourceName() {
        if (resourceName != null) {
            resourceName = resourceName.trim();
        }
    }

    @Documented
    @Target(FIELD)
    @Retention(RUNTIME)
    @Constraint(validatedBy = ResourceNameValidator.class)
    public @interface ValidResourceName {

        String message() default "Resource name is invalid.";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class ResourceNameValidator
            implements ConstraintValidator<ValidResourceName, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isEmpty()) {
                return violation(context, "Resource name is required.");
            }
            if (normalized.length() < 4) {
                return violation(context, "Minimum 4 characters.");
            }
            if (normalized.length() > 100) {
                return violation(context, "Maximum 100 characters.");
            }
            return true;
        }

        private boolean violation(ConstraintValidatorContext context, String message) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
            return false;
        }
    }
}
