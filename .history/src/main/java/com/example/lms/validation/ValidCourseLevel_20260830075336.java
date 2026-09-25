package com.example.lms.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CourseLevelValidator.class)
public @interface ValidCourseLevel {
    String message() default "Course Level must be one of: Beginner, Intermediate, Advanced";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
