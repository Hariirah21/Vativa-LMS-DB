package com.example.lms.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

public class CourseStatusValidator implements ConstraintValidator<ValidCourseStatus, String> {

    private static final Set<String> ALLOWED_STATUS = Set.of("Draft", "Upcoming", "Active", "Archived");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Allow null values (use @NotNull or @NotBlank if null is not allowed)
        if (value == null) {
            return true;
        }

        // Check if the value is in the allowed status values (case-insensitive)
        return ALLOWED_STATUS.stream().anyMatch(status -> status.equalsIgnoreCase(value.trim()));
    }
}
