package com.example.lms.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Generic checked-at-runtime exception carrying the HTTP status to return.
 * Used by SignUpService, LoginService and ForgotPasswordService so each
 * feature can raise business-rule errors (duplicate email, invalid OTP,
 * invalid credentials, etc.) without needing a separate exception class
 * per feature.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
