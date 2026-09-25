package com.example.lms.exception;

public class CourseListLoadException extends RuntimeException {

    public CourseListLoadException(Throwable cause) {
        super("Unable to load courses.", cause);
    }
}
