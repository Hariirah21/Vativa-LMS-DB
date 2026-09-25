package com.example.lms.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;

/**
 * Consolidated validators for Course DTO fields.
 * All course validation logic bundled in a single file.
 */
public class CourseValidators {

    // ==================== Course Level Validation ====================

    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = CourseLevelValidator.class)
    public @interface ValidCourseLevel {
        String message() default "Course Level must be one of: Beginner, Intermediate, Advanced";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class CourseLevelValidator implements ConstraintValidator<ValidCourseLevel, String> {
        private static final Set<String> ALLOWED_LEVELS = Set.of("Beginner", "Intermediate", "Advanced");

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return ALLOWED_LEVELS.stream().anyMatch(level -> level.equalsIgnoreCase(value.trim()));
        }
    }

    // ==================== Course Category Validation ====================

    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = CourseCategoryValidator.class)
    public @interface ValidCourseCategory {
        String message() default "Course Category must be one of: Technical, Soft Skills, Compliance, Leadership";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class CourseCategoryValidator implements ConstraintValidator<ValidCourseCategory, String> {
        private static final Set<String> ALLOWED_CATEGORIES = Set.of("Technical", "Soft Skills", "Compliance", "Leadership");

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return ALLOWED_CATEGORIES.stream().anyMatch(category -> category.equalsIgnoreCase(value.trim()));
        }
    }

    // ==================== Course Status Validation ====================

    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = CourseStatusValidator.class)
    public @interface ValidCourseStatus {
        String message() default "Course Status must be one of: Draft, Upcoming, Active, Archived";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class CourseStatusValidator implements ConstraintValidator<ValidCourseStatus, String> {
        private static final Set<String> ALLOWED_STATUS = Set.of("Draft", "Upcoming", "Active", "Archived");

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return ALLOWED_STATUS.stream().anyMatch(status -> status.equalsIgnoreCase(value.trim()));
        }
    }

    // ==================== Course Search Input Validation ====================

    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = SearchInputValidator.class)
    public @interface ValidSearchInput {
        String message() default "Search input must be alphanumeric with spaces, between 1 and 100 characters";
        int min() default 1;
        int max() default 100;
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class SearchInputValidator implements ConstraintValidator<ValidSearchInput, String> {
        private int min;
        private int max;

        @Override
        public void initialize(ValidSearchInput annotation) {
            this.min = annotation.min();
            this.max = annotation.max();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            // Allow null values (use @NotBlank if null is not allowed)
            if (value == null) {
                return true;
            }

            String trimmed = value.trim();
            
            // Check length
            if (trimmed.isEmpty() || trimmed.length() < min || trimmed.length() > max) {
                return false;
            }

            // Allow alphanumeric characters, spaces, hyphens, and underscores
            // Pattern: Letters (a-z, A-Z), numbers (0-9), spaces, hyphens, underscores
            return trimmed.matches("^[a-zA-Z0-9\\s\\-_]+$");
        }
    }
}
