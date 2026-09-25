package com.example.lms.exception;

import lombok.Getter;

@Getter
public class EnrollmentOperationException extends RuntimeException {

    public enum Operation {
        LIST_LOADING,
        ENROLLMENT,
        UNENROLLMENT
    }

    private final Operation operation;

    public EnrollmentOperationException(Operation operation, Throwable cause) {
        super("Enrollment operation failed: " + operation, cause);
        this.operation = operation;
    }
}
