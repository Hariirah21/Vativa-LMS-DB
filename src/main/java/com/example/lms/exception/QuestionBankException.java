package com.example.lms.exception;

import org.springframework.http.HttpStatus;

/**
 * The single feature-specific exception used by the Question Bank backend.
 * GlobalExceptionHandler already converts ApiException subclasses to the
 * project's standard ApiResponse error shape.
 */
public class QuestionBankException extends ApiException {

    public QuestionBankException(String message, HttpStatus status) {
        super(message, status);
    }
}
