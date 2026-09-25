package com.example.lms.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CourseStatusValidator.class)
public @interface ValidCourseStatus {
    String message() default "Course Status must be one of: Draft, Upcoming, Active, Archived";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
