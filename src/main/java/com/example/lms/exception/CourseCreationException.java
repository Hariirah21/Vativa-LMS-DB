package com.example.lms.exception;

public class CourseCreationException extends RuntimeException {
    public CourseCreationException(Throwable cause) {
        super("Unexpected failure while creating a course.", cause);
    }
}
