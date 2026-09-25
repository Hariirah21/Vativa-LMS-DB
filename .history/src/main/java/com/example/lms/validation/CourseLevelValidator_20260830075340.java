package com.example.lms.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

public class CourseLevelValidator implements ConstraintValidator<ValidCourseLevel, String> {

    private static final Set<String> ALLOWED_LEVELS = Set.of("Beginner", "Intermediate", "Advanced");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Allow null values (use @NotNull or @NotBlank if null is not allowed)
        if (value == null) {
            return true;
        }

        // Check if the value is in the allowed levels (case-insensitive)
        return ALLOWED_LEVELS.stream().anyMatch(level -> level.equalsIgnoreCase(value.trim()));
    }
}
