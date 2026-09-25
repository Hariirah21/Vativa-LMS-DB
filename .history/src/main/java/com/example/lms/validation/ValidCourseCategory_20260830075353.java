package com.example.lms.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CourseCategoryValidator.class)
public @interface ValidCourseCategory {
    String message() default "Course Category must be one of: Technical, Soft Skills, Compliance, Leadership";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
