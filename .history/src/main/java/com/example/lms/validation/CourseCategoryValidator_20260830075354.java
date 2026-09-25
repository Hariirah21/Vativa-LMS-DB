package com.example.lms.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

public class CourseCategoryValidator implements ConstraintValidator<ValidCourseCategory, String> {

    private static final Set<String> ALLOWED_CATEGORIES = Set.of("Technical", "Soft Skills", "Compliance", "Leadership");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Allow null values (use @NotNull or @NotBlank if null is not allowed)
        if (value == null) {
            return true;
        }

        // Check if the value is in the allowed categories (case-insensitive)
        return ALLOWED_CATEGORIES.stream().anyMatch(category -> category.equalsIgnoreCase(value.trim()));
    }
}
