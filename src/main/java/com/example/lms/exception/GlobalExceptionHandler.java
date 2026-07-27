package com.example.lms.exception;

import com.example.lms.dto.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Field-level validation errors (e.g. @NotBlank, @Pattern, @Email)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        ApiResponse<Map<String, String>> body = ApiResponse.<Map<String, String>>builder()
                .success(false)
                .message("Please correct the validation errors before submitting.")
                .data(fieldErrors)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    // Business-rule errors raised explicitly by the services
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.error(ex.getMessage()));
    }

    // Concurrent-signup race: two requests with the same email slip past the
    // existsByEmailIgnoreCase() pre-check at the same instant, and the DB's
    // unique constraint on `email` is what actually stops the second insert
    // (SignUpService catches nothing itself for this - it relies on this
    // handler). Must be registered BEFORE the generic Exception fallback,
    // otherwise this becomes a 500 instead of SRS's expected 409 + message.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Email ID already exists."));
    }

    // Safety net for any code still throwing ResponseStatusException directly,
    // so its status/message isn't flattened into a generic 500 by the fallback below.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.error(ex.getReason() != null
                        ? ex.getReason()
                        : "Request could not be processed."));
    }

    // Fallback - matches the SRS's "server unavailable" style messages
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Unable to process the request. Please try again later."));
    }
}